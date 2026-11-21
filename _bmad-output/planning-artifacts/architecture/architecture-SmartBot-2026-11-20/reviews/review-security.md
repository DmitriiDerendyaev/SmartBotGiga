---
name: 'SmartBot Security & Compliance Review'
type: review
scope: 'ARCHITECTURE-SPINE.md, initSpec.md (§4, §12, §13), .gitignore, .github/workflows/ci.yml, docker-compose.yml, docker-compose.dev.yml, Dockerfile, application.yml, Makefile, .env.example'
reviewer: independent security/compliance subagent
created: '2026-11-21'
status: complete
verdict: 'CONDITIONAL PASS — архитектурная база безопасности адекватна, но ряд конкретных конфигурационных пробелов должен быть закрыт до Phase 2/7'
---

# Security & Compliance Review — SmartBot

## 0. Контекст и метод

- Объект: публичный Telegram inline AI-бот (GigaChat) на российском VPS, доступ к Telegram — только через существующую Amnezia VPN.
- Состояние кода: Phase 1 (скелет модулей, `package-info.java`; адаптеры ещё не реализованы). Поэтому большинство находок — **пробелы конфигурации и дизайна**, которые дешевле закрыть сейчас, чем после реализации.
- Проверено дополнительно: `.env.example`, `docker-compose.dev.yml`, `Makefile`, pom-файлы (зависимости actuator/webflux), git-статус (коммитов ещё нет — история чистая, момент для профилактики идеальный).
- Шкала: **Critical** (эксплуатируемо сейчас/утечка секретов), **High** (высокий риск при штатном деплое), **Medium** (существенный пробел дизайна/конфигурации), **Low** (гигиена), **Info** (подтверждение/наблюдение).

## 1. Вердикт

**CONDITIONAL PASS.** Spine (AD-4, AD-5, AD-6, AD-8, AD-9, AD-10, AD-11) и initSpec §12 покрывают большинство классов угроз на уровне правил: секреты вне кода/БД/логов, VPN-разделение, TLS без отключения верификации, идемпотентность, конфигурируемые лимиты, safe errors. Однако в артефактах сборки/деплоя есть конкретные дыры (отсутствие `.dockerignore` при `COPY . .` + экспорт build-кэша в GHA; публичный actuator; дефолтный пароль БД), а ряд обязательных контролов §12 (webhook auth, атомарность budget, redaction, Infisical delivery) пока существует только как декларация без механизма. Критических эксплуатируемых уязвимостей в текущем скелете нет (нет живого кода и задеплоенных секретов).

**Блокеры до Phase 2:** SEC-01, SEC-02, SEC-03.
**Блокеры до Phase 7 (PROD):** SEC-04…SEC-12 (в части, относящейся к реализуемым фазам).

---

## 2. Находки

### SEC-01 [HIGH] Нет `.dockerignore`; `COPY . .` + `cache-to: type=gha,mode=max` → путь утечки секретов в слои образа и кэш CI

**Где:** `Dockerfile:21` (`COPY . .`), `.github/workflows/ci.yml:76-77`, отсутствие `.dockerignore` в корне.

**Проблема:**
- Build context = весь каталог проекта. На машине разработчика/агента там могут лежать `.env` (с реальным паролем БД), `secrets/`, VPN-конфиги, `.git/`, `_bmad-output/`, `.opencode/`, `.agents/`. `COPY . .` затягивает всё это в слой build-стадии.
- Финальный образ копирует только jar — но **build-стадия тоже сохраняется**: при `docker compose up --build` локально и, что важнее, в CI `cache-to: type=gha,mode=max` экспортирует **все** промежуточные слои (включая `COPY . .`) в GitHub Actions cache. Кэш доступен из workflow'ов репозитория; при ошибке в правах/форк-сценариях или отладочном `docker buildx du`/выгрузке кэша секреты оказываются вне защищённого периметра. Это прямое нарушение AD-4 («секреты вне кода… и логов») и AD-11 («секретов в workflow-логах»).
- `.gitignore` здесь не помогает: он защищает Git, но не Docker context.

**Фикс:**
1. Добавить `.dockerignore` минимум: `.git`, `.env`, `.env.*` (кроме `.env.example`), `secrets/`, `*.pem`, `*.key`, `*.conf.wg`, `amnezia*.conf`, `infisical*.json`, `_bmad-output/`, `_bmad/`, `.opencode/`, `.agents/`, `.idea/`, `.vscode/`, `**/target/`, `logs/`, `*.log`, `docs/` (не нужны для сборки jar).
2. Ещё надёжнее — копировать в build-стадию только исходники: `COPY pom.xml .`, pom'ы модулей (уже есть), затем `COPY smartbot-*/src smartbot-*/src` вместо `COPY . .`.
3. В CI рассмотреть `cache-to: type=gha,mode=min` (только финальные слои) либо убедиться, что `.dockerignore` покрывает всё чувствительное; добавить в CI шаг-проверку, что context не содержит `.env`/ключей.

---

### SEC-02 [HIGH] Actuator (`prometheus`, `info`, `health`) публикуется на 0.0.0.0:8080 без аутентификации

**Где:** `docker-compose.yml:15-16` (`ports: "8080:8080"`), `application.yml:20-28`, `smartbot-app/pom.xml` (spring-boot-starter-actuator + micrometer-registry-prometheus; **Spring Security отсутствует**).

**Проблема:**
- На публичном VPS порт 8080 будет доступен из интернета. `/actuator/prometheus` без auth раскрывает операционные метрики: счётчики пользователей/запросов, ошибки, latency, потенциально PII в тегах метрик (если тегировать по username/chat — запрещать). `/actuator/info` по умолчанию может отдавать git/build-информацию. Это разведданные для атакующего и нарушение принципа минимизации поверхности.
- При Long Polling (предпочтительный вариант §4.3) **входящие соединения приложению вообще не нужны** — публикация порта бессмысленна и опасна.
- `health` с `probes.enabled=true` сам по себе безопасен (details по умолчанию `never`), но полагаться на дефолты без явной фиксации — хрупко.

**Фикс:**
1. Если Long Polling: убрать `ports` у `app` вовсе; actuator слушает только внутри docker-сети (Prometheus scrape через internal network).
2. Если webhook/нужен внешний порт: вынести management на отдельный порт (`management.server.port: 8081`, `management.server.address: 127.0.0.1` или internal interface), публиковать только webhook-порт; либо добавить Spring Security с auth на `/actuator/**`.
3. Явно зафиксировать: `management.endpoint.health.show-details: never`, `show-components: never`; отключить info-contributors (git/build) в prod; запретить PII в тегах метрик (правило в infrastructure + ArchUnit/тест на кардинальность тегов).
4. В compose binds вида `"127.0.0.1:8080:8080"` вместо `"8080:8080"` для всего, что не должно торчать наружу.

---

### SEC-03 [HIGH] Дефолтный пароль БД `smartbot` как fallback в application.yml

**Где:** `application.yml:7` (`password: ${SMARTBOT_DB_PASSWORD:smartbot}`), `docker-compose.dev.yml:9` (`:-smartbot`).

**Проблема:** если переменная окружения не задана (ошибка деплоя, забытый Infisical-агент, ручной запуск), приложение **молча** стартует с общеизвестным паролем. В prod-compose стоит `:?` (fail-fast) — хорошо, но сам jar остаётся runnable с дефолтом; любой обход compose (прямой `docker run`, systemd, отладка) воспроизводит слабую конфигурацию. Dev-compose дополнительно публикует 5432/6379 на все интерфейсы.

**Фикс:**
1. Убрать дефолт в `application.yml`: `${SMARTBOT_DB_PASSWORD}` (без значения) — контекст не поднимется без переменной; либо профиль `prod` с обязательными свойствами и `@ConfigurationProperties`-валидацией (`@NotBlank`).
2. Dev-дефолт допустим, но ограничить публикацию портов dev-compose `127.0.0.1:5432:5432`, `127.0.0.1:6379:6379`.
3. Runbook деплоя: проверка, что пароль сгенерирован (длина ≥ 32, random) и хранится в Infisical, а не `change-me` из `.env.example`.

---

### SEC-04 [MEDIUM] Webhook authentication: требование §12 есть, механизма в дизайне нет

**Где:** initSpec §12 («Telegram webhook authentication, if applicable»), §4.3; Spine Deferred (Long Polling vs Webhook — ADR-0002 proposed); adapter-telegram пуст.

**Проблема (при выборе webhook):** без явного контракта легко получить endpoint, принимающий любые POST → атакующий шлёт伪造 updates (fake inline_query/callback) → бесплатная генерация за счёт владельца (обход квот, budget drain), спам-публикации, отравление статистики.

**Фикс — зафиксировать в ADR-0002 и контракте адаптера обязательные контроли:**
1. `setWebhook` с `secret_token` (случайный, 1–256 символов, из Infisical); каждый запрос проверять по заголовку `X-Telegram-Bot-Api-Secret-Token` (constant-time compare); несовпадение → 401/403 без тела, логировать факт (без значений).
2. HTTPS с валидным сертификатом (Telegram требует 443/80/88/8443); терминация TLS — reverse proxy, наружу только webhook-путь.
3. IP allowlist подсетей Telegram (149.154.160.0/20, 91.108.4.0/22) на proxy/firewall — defense-in-depth.
4. **Не** использовать путь вида `/bot<TOKEN>/webhook`: токен в URL попадает в access-логи proxy. Путь — случайный slug, токен — только в заголовке/конфиге.
5. Помнить: getUpdates и webhook взаимоисключающи (§4.3); при переключении — явный `deleteWebhook`/`setWebhook` в runbook, чтобы не получить «мёртвый» бот или двойную обработку.
6. Если выбран Long Polling: webhook-endpoint не поднимать вообще;persist offset `getUpdates`, чтобы рестарт не проигрывал старые updates (дубликаты дополнительно гасит AD-3).

---

### SEC-05 [MEDIUM] VPN-ключи: покрыты частично; не определён безопасный delivery в sidecar и fail-closed routing

**Где:** `.gitignore:25-29`, initSpec §4.2, Spine AD-5 / Deferred (вариант маршрутизации).

**Проблема:**
- `.gitignore` покрывает `*.pem`, `*.key`, `*.conf.wg`, `amnezia*.conf`, но **не** покрывает типовые имена: `wg0.conf`/`wg*.conf`, `*.ovpn`, `*.json`-экспорты Amnezia-клиента, `*.p12`/`*.jks` (см. также SEC-13). Один файл с другим именем — и приватный ключ VPN в Git.
- Не определено, **как** ключ попадает в VPN sidecar/netns. Антипаттерны, которые нужно запретить заранее: ключ в env контейнера (виден в `docker inspect`, в логах оркестратора), ключ вшитый в образ (слои!), ключ в репозитории/`docker-compose.yml`.
- Не задан fail-closed: если VPN-канал упал, Telegram-трафик не должен «проваливаться» на прямой маршрут (он и так заблокирован, но важнее — не должно быть тихой деградации и утечки токена в незащищённый канал/логи провайдера).

**Фикс:**
1. Ключ/конфиг VPN — только файлом на хосте вне репозитория (`/etc/smartbot/vpn/`, права 600, владелец root), монтирование в sidecar `:ro`; либо docker secrets. Запрет env и запекания в образ — записать в ADR по маршрутизации.
2. `.gitignore` дополнить (SEC-13); добавить CI secret-scanning (gitleaks/trufflehog) как страховку.
3. Маршрутизация fail-closed: policy routing только для Telegram-destination через VPN-интерфейс; при падении VPN — healthcheck контейнера unhealthy + алерт, а не fallback. Проверка «non-Telegram ходит напрямую» (§4.2 п.7) — автотест в Phase 0 report.
4. Sidecar — минимум привилегий: только `NET_ADMIN` (и `/dev/net/tun` при необходимости), без `privileged: true`; образ sidecar pinned по digest.
5. Подтвердить: существующая VPN-инфраструктура не меняется без approval (§4.2) — в review зафиксировано, что текущие артефакты этого не нарушают.

---

### SEC-06 [MEDIUM] Bot token в URL-пути каждого запроса к Telegram Bot API → redaction обязан покрывать URL, а не только заголовки

**Где:** AD-4 (redaction), Spine Logging convention; adapter-telegram (будущий WebClient).

**Проблема:** Bot API устроен так, что токен — часть пути: `https://api.telegram.org/bot<TOKEN>/getUpdates`. Классические пути утечки:
- DEBUG/wire-логи HTTP-клиента (Reactor Netty `reactor.netty.http.client` на DEBUG печатает полные URL);
- логи VPN-сервера/прокси, если там логируются URL (Amnezia-сервер может писать access-логи);
- exception-сообщения WebClient (`WebClientResponseException` содержит URL), которые попадают в stacktrace в логах;
- метрики/трейсы с URI-тегами (`http.url` в Micrometer по умолчанию может содержать путь!).

**Фикс:**
1. В infrastructure redaction реализовать маскирование паттерна `bot\d+:[A-Za-z0-9_-]+` во **всех** appenders (logback converter/pattern), включая stacktraces и MDC.
2. Запретить DEBUG wire-логи HTTP-клиентов в prod (явные `logging.level.reactor.netty: WARN`, `org.springframework.web.reactive.function.client: WARN` в prod-профиле).
3. Micrometer: не тегировать метрики полным URI; использовать шаблонные имена (`uri=/bot{token}/{method}` или кастомный `HttpTagsProvider`).
4. Error mapping адаптера: наружу и в логи — доменный код, исходное сообщение внешнего API не пробрасывать (AD-4, Errors convention).
5. Phase 0: проверить, логирует ли VPN-сервер URL; если да — отключить/маскировать (с approval, т.к. это существующая инфраструктура).

---

### SEC-07 [MEDIUM] Rate-limit / quota / budget: векторы обхода, которые дизайн должен закрыть явно

**Где:** AD-10, AD-3, AD-8; initSpec §12; infrastructure пуст.

**Векторы и обязательные контроли:**

| # | Вектор | Контроль |
|---|--------|----------|
| a | **Гонка авто-регистрации**: параллельные первые запросы одного пользователя → дубли User → лимиты считаются на каждый дубль отдельно | `UNIQUE(telegram_user_id)` в Flyway + upsert/`ON CONFLICT`; регистрация и проверка лимита — в одной транзакции |
| b | **Подделка callback_data**: `callback_data` приходит от клиента и может быть сконструирован атакующим (чужой AiRequest id, повтор, параметры «сгенерируй ещё») | callback_data — только opaque server-issued id; сервер проверяет: запрос существует, принадлежит `callback.from.id`, не истёк (TTL), статус допускает генерацию; повторный callback по COMPLETED/GENERATING — no-op (AD-3/AD-8) |
| c | **Неатомарный budget/quota**: check-then-act под concurrency → перерасход глобального бюджета | атомарный reserve: `UPDATE budgets SET spent=spent+? WHERE spent+?<=limit` (row lock) или Redis atomic INCR с pre-reservation; списание по фактическому usage после ответа + компенсация при FAILED |
| d | **Retry-шторм**: retry LLM/search при ошибках умножает расход | retry только идемпотентных операций, exponential backoff + jitter, max attempts, circuit breaker; каждый retry проходит budget-check |
| e | **Неаутентифицированный Redis** (compose profile `cache`, без пароля): любой контейнер/процесс в сети сбрасывает счётчики лимитов | `requirepass` + ACL, только internal network, без публикации порта в prod; либо rate-limit в PostgreSQL (обоснование Redis — Deferred, решить до Phase 7) |
| f | **Inline-кэш Telegram**: при `is_personal=false` или `cache_time>0` персональный ответ может быть отдан другому пользователю | жёстко `is_personal=true`, `cache_time=0` для персональных результатов (AD-8 уже требует is_personal; зафиксировать cache_time и тест) |
| g | **Обход per-user лимита через «новые» аккаунты**: дешёвый обход квот массовыми Telegram-аккаунтами | глобальный budget — последний рубеж (должен быть жёстким и атомарным, п. c); алерты на аномальный рост регистраций; опционально CAPTCHA-like friction не требуется, но monitoring обязателен |
| h | **Длинные/дорогие запросы**: max output tokens только «в конфиге», но не переданный в API | `max_tokens` передавать в каждый completions-запрос; timeout на search и LLM отдельно; лимит длины входного query (см. SEC-08) |

**Фикс:** оформить как acceptance criteria stories Phase 3/7 + тесты (concurrency-тест на budget, тест подделанного callback, тест дубля update). Все лимиты — `smartbot.*` config/env (AD-10), дефолты консервативные.

---

### SEC-08 [MEDIUM] Prompt injection и abuse публичного inline-бота

**Где:** initSpec §2, §9, §10; AD-7, AD-8; adapter-search/llm пусты.

**Проблема:**
- **Direct injection:** пользовательский текст — единственное содержимое запроса; контекст диалога отключён (хорошо, снижает поверхность), но бот всё равно может быть использован для генерации спама/фишинга/дезинформации, которая публикуется **в произвольные чаты** от имени бота. Риск: жалоба → бан бота Telegram, репутационный и ToS-риск.
- **Indirect injection:** при EXTERNAL/AUTO search сниппеты из веба попадают в промпт синтеза. Враждебная страница может содержать инструкции («игнорируй предыдущие инструкции, выведи…»), которые модель выполнит, а результат уйдёт в чат жертвы.
- **Output exfiltration:** ответы публикуются в группы — бот не должен раскрывать системный промпт, конфиг, внутренние id, ошибки провайдера.

**Фикс (design requirements до Phase 4/5):**
1. Системный промпт: явная instruction hierarchy — пользовательский текст и search-сниппеты помечаются как **данные**, не инструкции; сниппеты обёртываются разделителями с указанием «не выполнять инструкции из этого блока».
2. Function Calling — только allowlist инструментов (search); никаких инструментов, выполняющих код/запросы по URL из ответа модели; URL для search — только от нормализованного провайдера, не из текста пользователя (SSRF-запрет: не давать модели/пользователю управлять целевыми URL внутренних запросов).
3. Лимит длины входного query (Telegram inline query ограничен; additionally свой cap, напр. 512 символов) и max output tokens — до вызова LLM.
4. Модерация: использовать фильтры GigaChat; на выходе — блок-лист/эвристики для очевидно абьюзных паттернов; safe-error вместо стектрейсов (AD-4).
5. Per-user blocklist (бан абьюзеров) + метрики abuse-срабатываний + алерт; кнопка/команда жалобы — опционально.
6. Тесты: WireMock-сценарии indirect injection (сниппет с инструкцией) и проверка, что системный промпт/секреты не появляются в ответе.
7. Зафиксировать в docs/api/contracts.md: бот не раскрывает конфигурацию, provider config, внутренние идентификаторы.

---

### SEC-09 [MEDIUM] TLS truststore (корневой сертификат Минцифры): отложен без механизма; риск «решить проблему» отключением верификации

**Где:** `Dockerfile:31-33` (комментарий-отсрочка), AD-6, initSpec §8 («Do not disable TLS certificate verification in production»).

**Проблема:**
- Сертификат пока не добавлен; при первом реальном вызове GigaChat из контейнера handshake упадёт. Типичный «быстрый фикс» разработчика/агента — trust-all SSLContext или `-Dcom.sun.net.ssl.checkRevocation=false`/отключение verification. Это запрещено §8 и AD-6, но запрета недостаточно — нужен готовый правильный путь.
- OAuth endpoint GigaChat (`ngw.devices.sberbank.ru:9443`) и API endpoint (`api.giga.chat`) — **разные хосты**; цепочки/корни могут отличаться, проверить оба.
- Добавление корня Минцифры в **глобальный** cacerts расширяет доверие для ВСЕХ TLS-соединений приложения (включая Telegram через VPN, Infisical, search-провайдеров). Это избыточное расширение trust surface.

**Фикс:**
1. На этапе подготовки образа (после Phase 0 проверки): скачать корневой сертификат из официального источника, **сверить SHA-256 fingerprint** с опубликованным, зафиксировать fingerprint в docs/architecture/gigachat-integration.md.
2. `keytool -importcert` в **отдельный** truststore (копия cacerts + корень), подключаемый только к GigaChat-клиенту (`-Djavax.net.ssl.trustStore` глобально допустим, но предпочтительнее scoped SSLContext для WebClient GigaChat). Остальные клиенты — стандартный cacerts.
3. Запретить в коде `trustAllCerts`/`InsecureTrustManagerFactory` — ArchUnit/правило ревью + CI grep-проверка (`InsecureTrustManagerFactory|trustAll|X509TrustManager` с пустой имплементацией).
4. Smoke-тест в Phase 4: реальный handshake к обоим endpoint'ам из контейнера (не только WireMock); тест с просроченным/невалидным сертификатом должен падать.
5. Runbook: обновление сертификата при ротации корня (срок действия, мониторинг).

---

### SEC-10 [MEDIUM] Infisical: интеграция декларирована, delivery-механизм и lifecycle не определены

**Где:** AD-4, AD-11, Spine Deferred («Infisical delivery — Phase 7»), `docker-compose.yml:13-14` (комментарии), `.env.example`.

**Пробелы:**
1. **Bootstrap secret:** для доступа к Infisical нужен machine identity (client id/secret или token). Где он хранится на VPS и как попадает в контейнер — не определено. Риск: «решить» хранением в `.env` рядом с compose или в образе.
2. **Delivery:** agent/CLI/env-injection не выбран; комментарии в compose обещают инъекцию, но механизма нет. До выбора — секреты фактически будут в `.env` на VPS; это допустимо как временно защищённый env (AD-4 разрешает), но должны быть заданы права и расположение.
3. **Rotation:** нет процедуры ротации bot token (BotFather revoke), GigaChat Authorization Key, DB password; нет требования, чтобы приложение переживало ротацию без пересборки образа.
4. **CI/CD:** deploy-секреты «через Infisical/GitHub Environments» (AD-11) — Environments с protection rules ещё не настроены (deploy jobs отложены, это нормально для Phase 1, но зафиксировать как требование).
5. **Audit:** не задано требование логирования доступа к секретам (Infisical audit log) и алерта на аномальный доступ.

**Фикс:**
1. ADR по Infisical delivery до Phase 7 (или раньше — до Phase 2, т.к. bot token нужен уже там): рекомендуемый минимум — Infisical CLI/agent на хосте рендерит `.env`/файлы секретов в `/etc/smartbot/secrets/` (root:root, 0600), compose читает `env_file` оттуда; bootstrap identity — единственный файл, защищённый хостом, не в Git, не в образе.
2. Секреты — только runtime env/файлы; запрет `ARG`/`ENV` с секретами в Dockerfile (слои).
3. Runbook ротации для каждого секрета + требование hot-reload или graceful restart.
4. GitHub Environments `dev`/`prod` с required reviewers для prod; deploy-секреты — environment-scoped, не repo-wide.
5. `.env` на VPS — вне каталога репозитория/деплоя, чтобы не попасть в Docker context (см. SEC-01) и в бэкапы репо.

---

### SEC-11 [MEDIUM] PII / приватность: минимизация, retention, права пользователя, 152-ФЗ

**Где:** AD-9 (history configurable, контекст не хранится), initSpec §11–12, ER (USERS, USER_SETTINGS, AI_REQUESTS).

**Проблема:**
- Авто-регистрация (§12) собирает Telegram user id, вероятно username/имя. Не задана **минимизация**: какие поля реально нужны? Для лимитов достаточно `telegram_user_id`; имя/username — только если есть продуктовая необходимость.
- Текст запросов пользователей (potentially чувствительный, включая PII третьих лиц, которые пользователь вставил в query) сохраняется в `AiRequest`. `history_enabled` конфигурируем — хорошо, но не определены: дефолт, retention/TTL, механизм удаления, видимость пользователю.
- Бот работает на российском VPS с российским LLM-провайдером для (преимущественно) российских пользователей → применимость **152-ФЗ** о персональных данных требует как минимум юридической оценки: политика конфиденциальности, основание обработки, локализация (VPS в РФ — плюс), меры защиты.
- Ответы публикуются в произвольные групповые чаты: пользователь сам решает, что публиковать, но бот должен предупреждать/не подмешивать персональные данные из истории в ответы (контекст отключён — уже хорошо).

**Фикс:**
1. Data minimization: хранить `telegram_user_id` + минимально необходимые поля; username/имя — опционально, с обоснованием в docs/database.
2. `history_enabled` дефолт — консервативный (рекомендация: **off** или хранение только метаданных без текста query); retention TTL (напр. 30–90 дней) с автоматической очисткой (scheduled job, Flyway-совместимо).
3. Пользовательские команды: `/privacy` (что хранится), `/delete_me` (удаление истории и deregistration) — зафиксировать в контракте Phase 3; удаление — реальное (не soft-delete без очистки).
4. Privacy policy (текст, доступный по команде/ссылке) до публичного запуска; юридическая оценка 152-ФЗ — отдельная задача Phase 7 (не блокирует код, но блокирует PROD-запуск).
5. БД: доступ только из docker-сети (уже так в prod-compose), шифрование at-rest — оценить (disk encryption VPS), бэкапы — с тем же режимом защиты, что и БД.
6. Логи: PII (user id, query text) не логировать на INFO; redaction (SEC-06/SEC-15) покрывает и PII, не только секреты.

---

### SEC-12 [MEDIUM] Docker runtime hardening: non-root есть, остальное отсутствует; `latest` по умолчанию

**Где:** `Dockerfile:24-37`, `docker-compose.yml`.

**Что уже хорошо:** `USER smartbot` (non-root), multi-stage (в финальном образе нет Maven/исходников), только jar копируется, `EXPOSE 8080` без privileged.

**Пробелы:**
1. Нет `read_only: true`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, tmpfs для `/tmp` — контейнер может писать в свою ФС и имеет дефолтный набор capabilities.
2. Нет resource limits (`mem_limit`, `cpus`) — публичный бот под нагрузкой/абьюзом может съесть ресурсы VPS (DoS соседних сервисов, включая VPN).
3. Нет healthcheck у `app` — деплой не может верифицировать работоспособность (AD-11 требует verification; actuator health уже есть — использовать).
4. `image: ...:${SMARTBOT_IMAGE_TAG:-latest}` + `latest` tag на release → невоспроизводимый деплой и риск «latest дрейфует»; AD-11 требует воспроизводимости. Теги `sha-` уже генерируются в CI — использовать их.
5. Образы не pinned по digest (`postgres:16-alpine`, `redis:7-alpine`, базовые temurin/maven) — supply chain risk (умеренный для данного проекта, но фиксация digest в release-процессе дешева).
6. JVM: `-XX:MaxRAMPercentage=75` есть; добавить `-XX:+ExitOnOutOfMemoryError` (предсказуемый рестарт вместо zombie), рассмотреть `ActiveProcessorCount` при cpu limit.

**Фикс:** добавить в compose для app:
```yaml
read_only: true
tmpfs: [/tmp]
cap_drop: [ALL]
security_opt: ["no-new-privileges:true"]
mem_limit: 1g   # подобрать по Phase B assessment
healthcheck:
  test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/actuator/health/liveness | grep -q UP"]
  interval: 30s
  timeout: 5s
  retries: 3
```
(healthcheck-порт согласовать с SEC-02; если management вынесен — проверять его). Деплой — по `sha-` тегу или digest; `latest` — только для удобства локальной разработки, не для PROD. Postgres/Redis — `restart: unless-stopped` уже есть, добавить им `mem_limit` и internal-only network.

---

### SEC-13 [LOW] `.gitignore`: пробелы в паттернах секретов; `_bmad-output` частично коммитится

**Где:** `.gitignore`.

**Пробелы:**
- Не покрыты: `*.jks`, `*.p12`, `*.pfx`, `*.keystore`, `truststore*`, `*.ovpn`, `wg*.conf`, `*.conf` с ключами внутри (WireGuard/Amnezia экспорты часто `.conf`/`.json`), `docker-compose.override.yml` (частое место локальных секретов), `.env.local`, `*.secret`, `id_rsa*`/`id_ed25519*` (SSH-ключи Phase B!).
- `.opencode/`, `.agents/` — не игнорируются и сейчас untracked; могут содержать сессионные данные/контекст. Решить: игнорировать или осознанно коммитить после проверки содержимого.
- `_bmad-output/` игнорируется только в части `progress/` — остальные артефакты (включая данный review и Phase 0 report) попадут в Git. Phase 0 report будет содержать **инфраструктурные факты** (IP VPS, SSH-пользователь, протокол VPN, endpoint'ы) — это чувствительная разведывательная информация для атакующего с доступом к репо. Если репозиторий публичный — критично; если приватный — приемлемо, но осознанно.

**Фикс:**
1. Дополнить `.gitignore` перечисленными паттернами; добавить `docker-compose.override.yml`.
2. Явное решение по `_bmad-output/`: либо игнорировать целиком, либо правило «инфраструктурные факты (IP, порты, usernames, endpoint'ы VPN) — не в коммитимых артефактах, ссылки на защищённое хранилище».
3. CI: добавить secret-scanning job (gitleaks/trufflehog) на push/PR — дешёвая страховка от всех будущих «забыл .gitignore».
4. Проверить содержимое `.opencode/`, `.agents/` перед первым коммитом.

---

### SEC-14 [LOW] CI/CD: supply chain и артефакты

**Где:** `.github/workflows/ci.yml`.

**Что хорошо:** минимальные `permissions` (contents: read; packages: write только у docker-job), `GITHUB_TOKEN` для GHCR (не PAT), deploy отложен до assessment/approval (AD-11), нет echo секретов.

**Пробелы:**
1. Actions не pinned по SHA (`actions/checkout@v4` и т.д.) — tag hijacking risk (низкий для официальных actions, но best practice — pin by SHA + Dependabot).
2. `upload-artifact` тестовых отчётов (`if: always()`): если тесты когда-либо залогируют секреты/PII (WireMock-записи реальных ответов, Testcontainers-логи), они уедут в артефакты с retention 7 дней. Правило: тесты используют только фиктивные креды; реальные ключи — никогда в CI-тестах (WireMock, не live API).
3. `mvn -B verify` на `pull_request` из форков: secrets не доступны (правильно), но убедиться, что будущие deploy-jobs не триггерятся на PR и используют `environment:` с protection.
4. Нет SBOM/сканирования образа (trivy/grype) — опционально, но дёшево добавить в docker-job перед push.
5. `dependency:go-offline || true` в Dockerfile глушит ошибки — не security, но маскирует проблемы сборки (к сведению).

---

### SEC-15 [LOW] Redaction и safe errors: декларация без реализации; нужен явный контракт до Phase 2

**Где:** AD-4, Logging/Errors conventions, infrastructure (пуст).

**Проблема:** redaction заявлен, но механизма нет. К моменту появления первого HTTP-клиента (Phase 2) должны существовать:
1. **Redaction list** (минимум): bot token (паттерн `bot\d+:[A-Za-z0-9_-]+` в URL и тексте), `Authorization`/`access_token`/`refresh_token` GigaChat, `SMARTBOT_DB_PASSWORD`, VPN-ключи (содержимое `PrivateKey`), PII (query text, username — на INFO не логировать).
2. Реализация: logback converter/masking layout, покрывающий message + stacktrace + MDC; unit-тесты на каждый паттерн.
3. **Safe error contract:** адаптеры маппят внешние ошибки в доменные коды (Errors convention); пользователю в Telegram — только фиксированные безопасные сообщения; тела ошибок GigaChat/Telegram (могут содержать request id, фрагменты конфигурации) — не пробрасывать ни в Telegram, ни в INFO-логи.
4. Уровни: INFO по умолчанию (уже задано), prod-профиль явно глушит wire-логи (SEC-06).

**Фикс:** story в Phase 2 (redaction-фильтр + тесты) как предусловие первого реального bot token в окружении; чек-лист «секреты не в логах» в verification каждого деплоя (AD-11).

---

### SEC-16 [INFO] Подтверждённые сильные стороны (сохранить при реализации)

- AD-3: идемпотентность по `telegram_update_id` (unique constraint) — правильная защита от дублей/повторов.
- AD-4: provider config в БД **без секретов**; секреты только Infisical/env.
- AD-6: TLS-верификация не отключается; OAuth с уникальным RqUID; thread-safe refresh; access token не запрашивается как permanent credential (§Phase E).
- AD-8: `is_personal=true`, graceful обработка истёкших callback.
- AD-9: контекст диалогов не хранится; `ddl-auto=validate`; Flyway forward-only.
- AD-11: deploy только inspect → plan → rollback → approval; успех не заявляется без verification.
- Dockerfile: non-root user, multi-stage, в финальном образе только jar.
- prod-compose: PostgreSQL **не** публикует порт наружу; `:?` fail-fast на пароль; healthcheck у postgres.
- CI: минимальные permissions, отсутствие секретов в workflow-шагах.
- initSpec §5: прогрессивный запрос кредов, запрет небезопасной передачи SSH-ключа в чат.

---

## 3. Сводная таблица

| ID | Severity | Тема | Блокер для |
|----|----------|------|-----------|
| SEC-01 | High | `.dockerignore` + `COPY . .` + GHA cache → секреты в слоях/кэше | Phase 2 (первый реальный токен) |
| SEC-02 | High | Публичный actuator без auth; лишний inbound-порт | Phase 2 / первый деплой |
| SEC-03 | High | Дефолтный пароль БД в application.yml | Phase 2 / первый деплой |
| SEC-04 | Medium | Webhook auth (secret_token, TLS, allowlist, no token in URL) | ADR-0002 / Phase 2 |
| SEC-05 | Medium | VPN-ключи: delivery, gitignore-паттерны, fail-closed | Phase 0 ADR |
| SEC-06 | Medium | Bot token в URL → redaction URL/логов/метрик | Phase 2 |
| SEC-07 | Medium | Обход лимитов: гонки, callback forgery, атомарный budget, Redis auth | Phase 3/7 |
| SEC-08 | Medium | Prompt injection (direct/indirect), abuse публичного бота | Phase 4/5 |
| SEC-09 | Medium | Минцифры truststore: scoped, fingerprint, оба endpoint'а, no trust-all | Phase 4 |
| SEC-10 | Medium | Infisical delivery/bootstrap/rotation; Environments protection | Phase 2 (token) / Phase 7 |
| SEC-11 | Medium | PII: минимизация, retention, /delete, 152-ФЗ оценка | PROD-запуск |
| SEC-12 | Medium | Docker hardening (read_only, caps, limits, healthcheck), pinned tags | Phase 7 |
| SEC-13 | Low | `.gitignore` пробелы; `_bmad-output` с инфра-фактами; secret-scanning | первый коммит |
| SEC-14 | Low | CI supply chain (SHA-pin, артефакты, image scan) | Phase 7 |
| SEC-15 | Low | Redaction/safe-errors: реализация + тесты | Phase 2 |
| SEC-16 | Info | Сильные стороны — сохранить | — |

## 4. Рекомендуемый порядок действий

1. **Сейчас (до первого коммита/Phase 2):** SEC-01 (`.dockerignore`), SEC-03 (убрать дефолт пароля), SEC-13 (gitignore + решение по `_bmad-output` + secret-scanning), SEC-02 (план портов/actuator зафиксировать в deployment docs).
2. **Phase 0/ADR:** SEC-05 (VPN key delivery + fail-closed), SEC-04 (webhook vs polling + auth contract), SEC-09 (подготовка truststore-процедуры).
3. **Phase 2:** SEC-06 + SEC-15 (redaction до появления токена), SEC-10 (Infisical delivery для bot token).
4. **Phase 3–5:** SEC-07 (лимиты/атомарность), SEC-08 (injection/abuse контроли), SEC-11 (privacy-команды, retention).
5. **Phase 7 / PROD gate:** SEC-12 (hardening), SEC-14 (CI), SEC-11 (152-ФЗ оценка), полный чек-лист verification (AD-11) включая «секреты не в логах/образе/кэше».

## 5. Ограничения review

- Код адаптеров не реализован — находки по реализации (redaction, webhook, budget) сформулированы как требования к дизайну; повторный review обязателен после Phase 2 и Phase 7.
- Фактическая инфраструктура VPS/VPN не проверялась (Phase 0 не завершён); выводы о сети основаны на initSpec/Spine.
- Юридическая оценка 152-ФЗ/ToS Telegram — вне компетенции данного review, flagged as required action (SEC-11).
