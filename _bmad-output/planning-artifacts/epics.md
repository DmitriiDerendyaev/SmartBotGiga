---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics, step-03-create-stories, step-04-final-validation]
validation: >
  Автономный режим (делегировано пользователем до этапа размещения на сервере).
  FR1-20,22-25 покрыты stories; FR21 deferred. Миграции per-story: V2(1.2), V3(1.3),
  V4(2.2), V5(3.1). Forward-зависимостей нет; epics независимы (E1 mock standalone,
  E2 без E3 работает при NONE). Starter template не используется — Phase 1 skeleton
  уже создан и verified (BUILD SUCCESS).
inputDocuments:
  - initSpec.md
  - _bmad-output/planning-artifacts/PRD.md
  - _bmad-output/planning-artifacts/architecture/architecture-SmartBot-2026-11-20/ARCHITECTURE-SPINE.md
---

# SmartBot - Epic Breakdown

## Overview

Декомпозиция требований SmartBot (PRD derived из initSpec.md) и Architecture
Spine (AD-1…AD-26) на implementable stories. Режим: автономный (пользователь
делегировал решения до этапа размещения на сервере). UX-документ отсутствует
(стандартный Telegram inline UI).

## Requirements Inventory

### Functional Requirements

FR1: Бот принимает inline queries из любого чата; доступ публичный.
FR2: Возврат InlineQueryResultArticle; выбор публикует ответ в текущий чат.
FR3: Каждый запрос независим; история диалога не используется/не хранится.
FR4: Мгновенный лёгкий результат с кнопкой Generate Answer.
FR5: Callback: валидация → AiRequest → async generation (search → GigaChat).
FR6: Обновление опубликованного сообщения через editMessageText.
FR7: Graceful обработка отсутствующего inline_message_id / истёкших callback.
FR8: GigaChat OAuth: RqUID UUID, thread-safe single-flight refresh.
FR9: Модель конфигурируема; список моделей проверяется через API.
FR10: Streaming и Function Calling — где поддерживается (streaming deferred; FC в контракте).
FR11: Token usage per-call фиксируется.
FR12: Режимы поиска NONE/NATIVE/EXTERNAL/AUTO; не более одного провайдера на запрос.
FR13: Нормализованные SearchResult; исходные URL в финальном ответе.
FR14: Function Calling вызывает search tool; не интернет-доступ сам по себе.
FR15: Автоматическая регистрация при первом обращении.
FR16: Per-user rate limiting и квоты.
FR17: Global LLM budget, max output tokens, timeouts.
FR18: Идемпотентная обработка updates; защита от дублей.
FR19: Персистентность: User, UserSettings, AiRequest, AiProvider, SearchProvider, AiRequestSearch, AiRequestUsage (+ ProcessedUpdate ledger).
FR20: История configurable; приватность.
FR21: chosen_inline_result аналитика — опционально (out of scope initial release).
FR22: answerCallbackQuery немедленно; answerInlineQuery; is_personal=true, cache_time=0.
FR23: Лимиты длины query/результатов соблюдаются.
FR24: Безопасные ошибки наружу; redaction логов; credentials не через Telegram.
FR25: Provider-agnostic LlmProvider.

### NonFunctional Requirements

NFR1: Java 21, Spring Boot 4.2, Maven multi-module, hexagonal границы enforced (ArchUnit + enforcer).
NFR2: Telegram через Amnezia VPN; GigaChat напрямую; протокол не предполагать; VPN не менять без approval.
NFR3: Long Polling prefer; webhook альтернатива; ADR; взаимоисключение.
NFR4: TLS не отключать; Минцифры cert в scoped truststore; проверка до реализации.
NFR5: Секреты только Infisical/env; не в Git/логах/workflow; SSH не через чат.
NFR6: PostgreSQL 17 + Flyway; UUID/BIGINT; индексы/FK/unique; config без секретов.
NFR7: Docker/GHCR/GitHub Actions; feature→tests, develop→DEV, release→PROD; rollback; approval.
NFR8: JUnit 5(6), Testcontainers, WireMock; интеграции тестируются независимо.
NFR9: Micrometer; Prometheus/Grafana когда обосновано.
NFR10: Лимиты конфигурируемы.
NFR11: Документация §15; Mermaid; актуальность.
NFR12: Инкрементальность; отчёт в конце фазы; блокеры документируются.
NFR13: Redis только при обосновании (ADR-gate).
NFR14: Деплой-успех только после verification.

### Additional Requirements

- Architecture Spine AD-1…AD-26 — binding (см. ARCHITECTURE-SPINE.md).
- Сборка через Docker (ADR-0003); Phase 1 skeleton уже создан и verified.
- Phase 0 gate: реальные интеграции не верифицируются до infrastructure assessment; локально — mock/WireMock/Testcontainers.
- DEV/PROD изоляция: разные bot tokens/БД/Infisical environments (AD-11).
- Документация на русском, англ. термины.

### UX Design Requirements

Не применимо (стандартный Telegram inline UI). UX-контракт: FR4–FR7, FR24.

### FR Coverage Map

FR1: Epic 1 — inline query принимается из любого чата
FR2: Epic 1 — InlineQueryResultArticle, публикация выбором
FR3: Epic 1 — независимые запросы, без контекста
FR4: Epic 1 — мгновенный лёгкий результат + Generate Answer
FR5: Epic 2 — callback → async generation
FR6: Epic 2 — editMessageText обновление
FR7: Epic 1 (INVALID_CALLBACK) + Epic 2 (EXPIRED graceful)
FR8: Epic 2 — GigaChat OAuth/RqUID/single-flight refresh
FR9: Epic 2 — verified models, config
FR10: Epic 2 (FC в контракте порта) + Epic 3 (использование); streaming deferred
FR11: Epic 2 — per-call usage
FR12: Epic 3 — режимы поиска
FR13: Epic 3 — нормализация + sources
FR14: Epic 3 — FC → search tool
FR15: Epic 1 — get-or-create регистрация
FR16: Epic 4 — rate limits/квоты
FR17: Epic 4 — budget/лимиты enforcement (maxOutputTokens передаётся уже в Epic 2)
FR18: Epic 1 — ledger + partial unique (бизнес-идемпотентность)
FR19: Epic 1 (users/requests/ledger) + Epic 2 (usage) + Epic 3 (searches)
FR20: Epic 4 — history/privacy/retention
FR21: deferred (out of scope initial release)
FR22: Epic 1 — answerInlineQuery/answerCallbackQuery, is_personal, cache_time=0
FR23: Epic 1 — MessageLimits, валидация длины
FR24: Epic 1 (ErrorCode, redaction, safe messages) + Epic 4 (abuse-контроли)
FR25: Epic 2 — provider-agnostic LlmProvider

NFR coverage: NFR1 — все epics (ArchUnit/enforcer в CI); NFR2/3/4 — Epic 5 (+AD-5/6 binds ранее);
NFR5 — Epic 1 (redaction) + Epic 5 (Infisical); NFR6 — Epic 1–3 (миграции);
NFR7/14 — Epic 5; NFR8 — все epics; NFR9 — Epic 4; NFR10 — Epic 4;
NFR11/12 — все epics (отчёт в конце каждого); NFR13 — Deferred (ADR-gate).

## Epic List

### Epic 1: Мгновенный inline-отклик
Пользователь набирает `@SmartBot <запрос>` в любом чате и мгновенно получает
лёгкий результат с кнопкой **Generate Answer** (генерация пока mock).
Бот идентифицирует пользователя, регистрирует при первом обращении, защищён
от дублей и небезопасных ошибок. Vertical slice: Telegram adapter (mock LLM) →
use cases → PostgreSQL.
**FRs covered:** FR1, FR2, FR3, FR4, FR7 (частично), FR15, FR18, FR19 (ядро), FR22, FR23, FR24 (каталог/redaction)
**ADs:** AD-1…5, AD-8, AD-9, AD-13, AD-14, AD-17, AD-19, AD-20, AD-23
**Зависимости:** Phase 1 skeleton (готов). Реальная Telegram-верификация — Epic 5; локально WireMock.

### Epic 2: AI-ответ по кнопке
Пользователь нажимает Generate Answer — бот асинхронно генерирует ответ через
GigaChat и обновляет опубликованное сообщение. OAuth, token refresh, usage
учёт, lease/recovery, graceful истёкшие callback.
**FRs covered:** FR5, FR6, FR7 (полностью), FR8, FR9, FR10 (контракт FC), FR11, FR25
**ADs:** AD-6, AD-8, AD-15, AD-16, AD-18 (maxOutputTokens/timeout), AD-21, AD-23, AD-24
**Зависимости:** Epic 1. Реальный GigaChat-вызов — после Phase E credentials (Epic 5 gate); локально WireMock.

### Epic 3: Ответы с источниками
Пользователь получает ответы, синтезированные с web-поиском, с сохранёнными
URL источников; режимы NONE/NATIVE/EXTERNAL/AUTO; защита от indirect injection.
**FRs covered:** FR12, FR13, FR14, FR10 (использование FC)
**ADs:** AD-7, AD-19 (prompt budget), AD-24, AD-25
**Зависимости:** Epic 2. Внешний провайдер — Phase F (абстракция + mock/WireMock до выбора).

### Epic 4: Публичный бот под контролем
Оператор может безопасно открыть бота публике: per-user rate limits, квоты,
атомарный global budget, blocklist, abuse-метрики; пользователь управляет
приватностью (/privacy, /delete_me, retention). Мониторинг расходов и ошибок.
**FRs covered:** FR16, FR17, FR20, FR24 (усиление)
**ADs:** AD-10, AD-17 (blocked), AD-18, AD-25, AD-26
**Зависимости:** Epic 1–3 (порты лимитов вызываются из существующих use cases).

### Epic 5: Надёжная работа на российском VPS
Бот реально работает в Telegram с российского VPS: Phase 0 assessment,
VPN-маршрут (fail-closed), проверка GigaChat TLS (Минцифры), Infisical delivery,
DEV/PROD изоляция, CI/CD deploy с rollback, E2E verification, runbooks.
**FRs covered:** верификация всех FR на реальной инфраструктуре; NFR2, NFR3, NFR4, NFR5, NFR7, NFR14
**ADs:** AD-5, AD-6 (truststore), AD-11, AD-12, AD-23
**Зависимости:** Epic 1–4; **gate: данные VPS/SSH/VPN/bot tokens от пользователя** — точка остановки автономного режима.

## Epic 1: Мгновенный inline-отклик

Пользователь набирает `@SmartBot <запрос>` в любом чате и мгновенно получает лёгкий результат с кнопкой Generate Answer; нажатие запускает mock-генерацию и обновляет сообщение. Полный vertical slice с безопасными ошибками, регистрацией и идемпотентностью.

### Story 1.1: Безопасные ошибки и redaction логов

As a пользователь Telegram,
I want получать только безопасные сообщения об ошибках, а мои данные и секреты бота не попадали в логи,
So that бот не раскрывал внутренности и credentials при сбоях.

**Acceptance Criteria:**

**Given** каталог `ErrorCode` (enum в smartbot-domain, AD-20)
**When** любой адаптер маппит внешнюю ошибку
**Then** наружу передаётся только ErrorCode + типизированные безопасные параметры; сырые тела/заголовки не пересекают порт
**And** единственная таблица ErrorCode→безопасный текст живёт в application-api/impl; ArchUnit/IT-страж запрещает user-facing путям принимать String message из адаптера

**Given** logback-конфигурация с masking converter (infrastructure)
**When** в лог попадает сообщение/stacktrace/MDC с паттерном `bot\d+:[A-Za-z0-9_-]+`, Authorization-заголовком или PrivateKey
**Then** значения замаскированы во всех appender'ах; unit-тест на каждый паттерн зелёный
**And** wire-логи HTTP-клиентов ограничены WARN; PII не логируется на INFO

### Story 1.2: Распознавание и автоматическая регистрация пользователя

As a пользователь Telegram,
I want бот узнавал меня при первом обращении без регистрации,
So that я мог сразу пользоваться ботом, а оператор — применять лимиты.

**Acceptance Criteria:**

**Given** миграция V2 (users, user_settings; UUID id, BIGINT telegram_user_id unique; status/history_enabled; AD-9/17/26)
**When** приходит первый update от пользователя (inline_query или callback)
**Then** application-impl атомарно get-or-create (`INSERT … ON CONFLICT DO UPDATE` только profile-полей и last_seen_at); адаптер передаёт TelegramPrincipal и не регистрирует сам
**And** upsert не трогает status и history_enabled (default off); blocked меняется только явным use case

**Given** два параллельных первых update одного пользователя
**When** оба обрабатываются
**Then** создаётся ровно один User (concurrency-тест на Testcontainers PostgreSQL); проверка blocked выполняется до создания AiRequest

### Story 1.3: Inline query и персистентная корреляция

As a пользователь Telegram,
I want мой запрос сохранялся и был связан с кнопкой генерации,
So that ответ можно было запустить позже, даже после перезапуска бота.

**Acceptance Criteria:**

**Given** миграция V3 (ai_requests со статусами/lease/delivery_error_code/truncation-полями, processed_updates ledger; partial unique «один активный на inline_message_id»; AD-13/14/15/19)
**When** HandleInlineQueryUseCase получает query + TelegramPrincipal
**Then** создаётся AiRequest CREATED с query_text и opaque correlation_token (≤64 байт, генерация/формат — в impl/api); повторный inline_query (тот же текст+пользователь, окно TTL) переиспользует запись
**And** query_text в БД идентичен тексту, который уйдёт в LLM; валидация длины — отказ MESSAGE_TOO_LONG/VALIDATION без обрезки

**Given** повторная доставка update с тем же telegram_update_id
**When** обработка
**Then** ledger processed_updates гасит дубль (тест); AiRequest не создаётся повторно

**Given** граф переходов AiRequestStatus в domain
**When** попытка недопустимого перехода или мутации терминального статуса
**Then** domain-метод отклоняет (unit-тесты на все переходы AD-15); переходы в persistence — атомарный CAS

### Story 1.4: Telegram polling и мгновенный лёгкий результат

As a пользователь Telegram,
I want набрать @SmartBot запрос и мгновенно увидеть результат с кнопкой Generate Answer,
So that я не ждал генерацию и мог опубликовать результат в свой чат.

**Acceptance Criteria:**

**Given** adapter-telegram на WebClient с конфигурируемым base URL (WireMock в тестах) и polling-цикл getUpdates
**When** приходит inline_query
**Then** вызывается HandleInlineQueryUseCase и отправляется answerInlineQuery с InlineQueryResultArticle (is_personal=true, cache_time=0), кнопкой Generate Answer и opaque callback_data=token; адаптер не парсит token
**And** бюджет MessageLimits (4096/длина query/64 байт callback_data) экспортирован адаптером и используется impl (AD-19)

**Given** семантика at-least-once (AD-23)
**When** offset getUpdates подтверждается
**Then** только после персистентной обработки (ledger); offset персистится; graceful shutdown дожидается in-flight; тест «краш до ack → повторная доставка гасится ledger»

**Given** невалидный/слишком длинный query
**When** обработка
**Then** пользователю — безопасное сообщение из каталога ErrorCode; сырые тексты ошибок Telegram API не рендерятся

### Story 1.5: Приём и валидация callback

As a пользователь Telegram,
I want нажатие Generate Answer запускало генерацию только для моего непросроченного запроса,
So that чужие/подделанные/повторные нажатия не тратили ресурсы и не ломали UX.

**Acceptance Criteria:**

**Given** контракт HandleCallbackUseCase(token, inlineMessageId, principal) в application-api
**When** приходит валидный callback
**Then** answerCallbackQuery отправляется немедленно (до генерации); сервер проверяет: запись существует, принадлежит callback.from.id, не истекла (TTL), статус допускает генерацию; повторный callback по тому же inline_message_id идемпотентно возвращает существующий запрос (AD-13/14)

**Given** forged token, чужой владелец, истёкший TTL или callback без inline_message_id
**When** обработка
**Then** доменные коды INVALID_CALLBACK/EXPIRED; EXPIRED ставит только impl через use case; пользователю — безопасный alert; падения нет (тесты на каждый случай)

### Story 1.6: Mock-генерация и обновление сообщения

As a пользователь Telegram,
I want после нажатия кнопки увидеть сгенерированный (пока mock) ответ в опубликованном сообщении,
So that полный UX работал end-to-end до подключения реального LLM.

**Acceptance Criteria:**

**Given** порт LlmProvider в application-api (синхронный complete + capabilities, AD-6) и MockLlmProvider (static response)
**When** валидный callback переводит AiRequest CREATED→GENERATING (CAS) и in-process executor запускает генерацию
**Then** lease/heartbeat обновляется исполнителем; по завершении GENERATING→COMPLETED; финальный текст (обрезка тела + sources-блок, HTML) собирает impl; adapter отправляет as-is через порт InlineMessageEditor (editMessageText)

**Given** ошибка доставки (message not found / истёкшее сообщение)
**When** editMessageText fails
**Then** COMPLETED + delivery_error_code (AD-15); «message is not modified» = успех; transport-retry 429 — внутри адаптера (WireMock-тесты)

**Given** рестарт приложения с осиротевшим GENERATING (lease истёк)
**When** recovery sweep при старте
**Then** запрос → FAILED RESTARTED; watchdog TTL переводит зависшие CREATED/GENERATING в EXPIRED; проигравший CAS не редактирует сообщение (тесты гонки watchdog vs generation)

## Epic 2: AI-ответ по кнопке

Пользователь получает реальный AI-ответ GigaChat вместо mock: OAuth, completions, usage-учёт, конфигурируемые модели и лимиты.

### Story 2.1: GigaChat OAuth и жизненный цикл токена

As a оператор бота,
I want GigaChat-адаптер сам получал и обновлял access token,
So that генерация работала непрерывно без ручного управления токенами.

**Acceptance Criteria:**

**Given** Authorization Key и scope из env/Infisical (не в коде/БД)
**When** адаптер запрашивает OAuth token (документированный endpoint, WireMock в тестах)
**Then** каждый запрос несёт уникальный RqUID UUID; refresh — по expiration из ответа с configurable safety margin; single-flight: N параллельных 401 → один OAuth-запрос, остальные ждут результат (concurrency-тест)

**Given** scoped truststore для GigaChat-клиента (AD-6)
**When** создаётся WebClient
**Then** используется отдельный truststore (не глобальный cacerts); trust-all запрещён (CI grep InsecureTrustManagerFactory|trustAll); конфигурация готова к добавлению корня Минцифры в Epic 5

### Story 2.2: Completions, error mapping и per-call usage

As a оператор бота,
I want каждый LLM-вызов учитывал токены и стоимость,
So that расходы были прозрачны и бюджет считался по факту.

**Acceptance Criteria:**

**Given** GigaChatProvider.complete(LlmRequest) (POST https://api.giga.chat/v1/chat/completions, WireMock)
**When** вызов выполняется
**Then** maxOutputTokens из LlmRequest передаётся в API и не превышает; transport-retry (429/5xx, backoff+jitter, cap) — внутри complete() в пределах call timeout; impl не ретраит (AD-21); ошибки маппятся в ErrorCode без сырых тел

**Given** миграция V4 (ai_request_usage: unique(request_id, call_index), без UK на request_id; AD-16)
**When** LlmResponse возвращается с usage/latencyMs
**Then** единственный писатель usage — application-impl (per-call строка); estimated_cost считается по smartbot.pricing.*; adapter ничего не персистит (тест-страж)

### Story 2.3: Реальная генерация вместо mock

As a пользователь Telegram,
I want получать осмысленный AI-ответ на мой запрос,
So that бот приносил реальную пользу.

**Acceptance Criteria:**

**Given** эффективная конфигурация (UserSettings → ai_providers.config → smartbot.* defaults, AD-24)
**When** callback запускает генерацию
**Then** GigaChatProvider вызывается с эффективной моделью; проверенный список моделей хранится в ai_providers.config и обновляется явным use case после верификации через API; default_model входит в список (тест); disabled provider → FAILED PROVIDER_UNAVAILABLE без INTERNAL

**Given** иерархия timeout (AD-18): request TTL > generation timeout > LLM call timeout (incl. retries) > HTTP timeout
**When** конфигурация задана
**Then** соотношение enforced тестом; E2E на WireMock: callback → GigaChat → editMessageText с реальным текстом ответа; mock-провайдер остаётся только в test-профиле

## Epic 3: Ответы с источниками

Пользователь получает ответы с актуальной информацией из web и сохранёнными URL источников; режимы поиска управляемы.

### Story 3.1: Абстракция поиска и режимы

As a пользователь Telegram,
I want режим поиска учитывал мои настройки (NONE/NATIVE/EXTERNAL/AUTO),
So that я контролировал, ищет ли бот в интернете.

**Acceptance Criteria:**

**Given** SearchResult в domain (канонические типы, AD-7) и порт SearchProvider; миграция V5 (search_providers, ai_request_searches; jsonb — каноническая форма)
**When** impl вычисляет эффективный режим (UserSettings → provider config → global default)
**Then** NONE — поиск не выполняется; NATIVE — используются возможности модели; EXTERNAL/AUTO — вызывается внешний провайдер; за запрос не более одного необходимого провайдера; NoopSearchProvider для NONE покрыт тестами

### Story 3.2: Внешний search-адаптер с нормализацией

As a пользователь Telegram,
I want результаты поиска были чистыми и единообразными,
So that ответы не содержали мусорных ссылок и дублей.

**Acceptance Criteria:**

**Given** конфигурируемый HTTP SearchProvider (конкретный вендор — Phase F; контрактные тесты на WireMock)
**When** провайдер возвращает сырые результаты
**Then** adapter нормализует до возврата: url http(s) без tracking-параметров, snippet cap, publicationDate Instant?, плоский Map<String,String> metadata; impl валидирует и дедуплицирует по url; в jsonb попадает только каноническая форма (тесты на каждой границе)

### Story 3.3: Синтез с источниками и защита от injection

As a пользователь Telegram,
I want ответ содержал нумерованный список источников с оригинальными URL и не выполнял инструкции из веб-страниц,
So that я мог проверить информацию и был защищён от indirect injection.

**Acceptance Criteria:**

**Given** Function Calling в контракте LlmProvider (allowlist: только search; AD-25)
**When** GigaChat возвращает tool call
**Then** impl выполняет search через порт и возвращает tool-response; несколько completions на запрос → per-call usage (AD-16); URL для поиска не берутся из текста пользователя/модели (SSRF-тест)

**Given** prompt builder с instruction hierarchy
**When** сниппеты попадают в prompt
**Then** они обёрнуты разделителями «данные, не инструкции»; snippet cap и prompt budget configurable (impl); WireMock-сценарий indirect injection не приводит к выполнению инструкций сниппета; системный промпт/секреты не появляются в ответе (тест)

**Given** финальный текст с sources-блоком
**When** ответ длиннее 4096 символов
**Then** обрезается только тело; нумерованный sources-блок с оригинальными URL не обрезается никогда (AD-19); в БД — полный и отправленный текст + флаг truncation

## Epic 4: Публичный бот под контролем

Оператор безопасно открывает бота публике: лимиты, бюджет, приватность, abuse-мониторинг.

### Story 4.1: Per-user rate limits и квоты

As a оператор бота,
I want каждый пользователь имел конфигурируемые лимиты запросов,
So that публичный бот не расходовал ресурсы без ограничений.

**Acceptance Criteria:**

**Given** порты RateLimitPort/QuotaPort (application-api), in-memory реализация (infrastructure)
**When** callback обрабатывается
**Then** проверки blocked→rate limit→quota выполняются в impl в фиксированном порядке до GENERATING (AD-18); отказ = AiRequest FAILED с кодом; attempt не потребляет квоту генерации, но учитывается в rate-limit окне (обе семантики в тестах); infrastructure не использует AOP

**Given** конкурентные запросы одного пользователя
**When** лимит достигается
**Then** ровно limit запросов проходят (concurrency-тест); значения — smartbot.limits.*, дефолты консервативные

### Story 4.2: Атомарный global LLM budget

As a оператор бота,
I want глобальный бюджет расходов соблюдался атомарно,
So that массовые аккаунты и retry-штормы не обходили лимиты.

**Acceptance Criteria:**

**Given** BudgetPort с атомарным reserve/settle/release
**When** генерация выполняется
**Then** reserve до call → settle по per-call usage → release остатка; каждый retry проходит budget-check; check-then-act гонок нет (concurrency-тест на перерасход); исчерпание → FAILED BUDGET_EXCEEDED + алерт-метрика

### Story 4.3: Приватность: /privacy, /delete_me, retention

As a пользователь Telegram,
I want знать, что хранится, и удалить свои данные,
So that моя приватность соблюдалась.

**Acceptance Criteria:**

**Given** history_enabled default off (AD-26)
**When** пользователь отправляет /privacy
**Then** бот отвечает, какие данные хранятся; при /delete_me — реальное удаление истории и deregistration (не soft-delete); retention TTL (дефолт 30 дней) с автоочисткой scheduled job (тесты на Testcontainers)

**Given** history_enabled=false
**When** запрос обрабатывается
**Then** текст query не сохраняется (только метаданные); PII не в INFO-логах

### Story 4.4: Abuse-мониторинг и blocklist

As a оператор бота,
I want видеть abuse и блокировать нарушителей,
So that бот не использовали для спама и не забанил Telegram.

**Acceptance Criteria:**

**Given** per-user blocklist (status blocked, AD-17)
**When** blocked пользователь обращается
**Then** отказ BLOCKED до создания AiRequest и списания квоты; разблокировка — только явный admin/use-case путь

**Given** метрики Micrometer (abuse-срабатывания, регистрации, отказы лимитов, budget)
**When** аномальный рост регистраций/отказов
**Then** алерт-метрика срабатывает; выходные эвристики + фильтры GigaChat применяются; дашборд/правила документированы в docs/runbooks; тесты на forged callback и дубли update уже зелёные (регрессия)

## Epic 5: Надёжная работа на российском VPS

Бот реально работает в Telegram с российского VPS. **Gate: данные инфраструктуры от пользователя (Phase B/C/D/E).**

### Story 5.1: Phase 0 — read-only infrastructure assessment

As a оператор бота,
I want инфраструктура была проверена до изменений,
So that деплой не сломал существующий VPN и не упёрся в блокировки.

**Acceptance Criteria:**

**Given** VPS/SSH данные (SSH agent/локальный ключ, не чат) и Amnezia-конфигурация
**When** проводится read-only assessment
**Then** зафиксированы: ОС/ресурсы/Docker, фактический протокол VPN, доступность Telegram через VPN, GigaChat напрямую (DNS/TLS/оба endpoint'а); connectivity report создан (чувствительные факты — не в Git, AD-12); изменения инфраструктуры — только после approval

**Given** результаты проверки канала
**When** выбирается транспорт и маршрут
**Then** ADR-0002 финализирован (polling/webhook); ADR маршрутизации (sidecar/netns/host) принят; blockers documented

### Story 5.2: VPN-маршрут и реальная Telegram-верификация

As a пользователь Telegram,
I want бот отвечал в реальных чатах,
So that продукт работал с российского VPS.

**Acceptance Criteria:**

**Given** утверждённый вариант маршрутизации; ключ — файлом 0600 ro-монт (AD-5)
**When** разворачивается VPN sidecar/netns
**Then** Telegram-трафик идёт через VPN (fail-closed: падение VPN → unhealthy+алерт, не fallback); non-Telegram — напрямую (автотест split routing); sidecar без privileged; существующая VPN-инфраструктура не изменена

**Given** реальный bot token (Infisical) и включённый Inline Mode
**When** E2E: @bot query в реальном чате → кнопка → генерация → edit
**Then** полный цикл verified; один poller на token; offset/ledger переживают рестарт

### Story 5.3: GigaChat production-подключение

As a пользователь Telegram,
I want ответы генерировал реальный GigaChat,
So that бот давал настоящие AI-ответы.

**Acceptance Criteria:**

**Given** Authorization Key/scope из Infisical; корень Минцифры с сверенным SHA-256 fingerprint
**When** scoped truststore собран и подключён к GigaChat-клиенту
**Then** реальный handshake к OAuth и API endpoint'ам успешен из контейнера; тест с невалидным сертификатом падает; fingerprint зафиксирован в docs; реальный completion + verified models список обновлён; usage/тарифы сверены с аккаунтом

### Story 5.4: Секреты и изоляция окружений

As a оператор бота,
I want DEV и PROD были изолированы, а секреты доставлялись безопасно,
So that тестовый бот не воровал updates прода и секреты не утекали.

**Acceptance Criteria:**

**Given** ADR Infisical delivery (CLI/agent → /etc/smartbot/secrets/ 0600, env_file)
**When** разворачиваются окружения
**Then** DEV и PROD: разные bot tokens/боты, разные БД, разные Infisical environments, profiles dev/prod (AD-11); bootstrap identity — единственный защищённый файл; ротация секретов без пересборки образа (runbook); GitHub Environments dev/prod с required reviewers для prod

### Story 5.5: CI/CD deploy, rollback и PROD-verification

As a оператор бота,
I want деплой был воспроизводимым с rollback,
So that обновления не ломали прод и откатывались безопасно.

**Acceptance Criteria:**

**Given** deploy jobs (develop→DEV, release→PROD) в GitHub Actions
**When** деплой выполняется
**Then** образ по sha-тегу/digest (не latest); inspect→plan→rollback→approval соблюдены; rollback = redeploy предыдущего sha + verification; секреты не в логах workflow; hardening (read_only/caps/limits/healthcheck) verified

**Given** PROD-деплой завершён
**When** smoke-тест E2E в реальном Telegram + проверка «секреты не в логах/образе/кэше»
**Then** успех заявлен только после verification (AD-11/NFR14); runbooks deployment/troubleshooting актуальны; privacy policy опубликована; чек-лист 152-ФЗ оценки закрыт (gate PROD-запуска, AD-26); финальный отчёт фазы по §16
