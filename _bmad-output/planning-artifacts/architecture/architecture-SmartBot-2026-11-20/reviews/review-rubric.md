# Rubric Review — ARCHITECTURE-SPINE.md (SmartBot, 2026-11-20)

- **Reviewer:** independent architecture rubric walker (subagent)
- **Date:** 2026-11-21
- **Inputs:** ARCHITECTURE-SPINE.md (draft), initSpec.md (§3–15, Phases 0–7), .memlog.md
- **Verdict:** **pass-with-fixes** — spine годен как build-substrate для epics/stories после закрытия 3 High и 4 Medium находок. Critical нет.

---

## 1. Оценка по чек-листу

| Критерий | Оценка | Комментарий |
| --- | --- | --- |
| Фиксирует реальные точки дивергенции для epics/stories, ничего не упущено | **частично** | Границы модулей, владение AiRequest, секреты, VPN-разделение, GigaChat-контракт зафиксированы хорошо. Упущены: идемпотентность генерации на уровне `inline_message_id` (F1), recovery/timeout/retry семантика async-генерации (F2), стратегия разделения DEV/PROD (F3), форма порта `LlmProvider` (F4). |
| Каждый AD enforceable и реально предотвращает заявленную дивергенцию | **частично** | AD-1/AD-2: ArchUnit проверяет код, но не pom-зависимости; граф AD-2 неполон («полный список — в pom»), что делает правило «единственная топология» непроверяемым в текущем виде (F6). AD-3: unique по `update_id` не предотвращает дубли генераций (F1). AD-10: не сказано, как impl вызывает лимиты, если ребра IMPL→INFRA нет (F5). Остальные AD (4, 5, 6, 7, 8, 9, 11, 12) enforceable. |
| Под Deferred нет ничего, что позволит двум независимо собранным юнитам разойтись | **частично** | Async in-process [ASSUMPTION] без recovery-семантики — риск расхождения Phase 3 и Phase 6 (F2). DEV/PROD топология deferred без стратегии изоляции окружений — риск конфликта getUpdates (F3). Redis deferred без дефолта для rate limiter (в рамках F5). VPN-вариант, polling/webhook, SDK vs REST, Infisical delivery, Prometheus — безопасно deferred (один юнит либо сетевой уровень, приложение агностично). |
| Named tech verified-current | **да** | Spring Boot 4.2.0, ArchUnit 1.5.1, WireMock 3.13.2 (4.x beta отклонён осознанно), Testcontainers через Boot BOM — соответствуют проверке по Maven Central 2026-11-20. Замечания: skeleton ещё на 3.5.7 (зафиксировано в memlog, bump обязателен в Phase 1) и PostgreSQL 16 при актуальной линии 18 — допустимо, 16 поддерживается (F11, low). |
| Покрытие возможностей initSpec §3–15, Phases 0–7 | **в основном да** | Phase 0 → AD-5/AD-12 + Open Questions; Phase 1 → AD-11 + Structural Seed; Phase 2 → AD-8; Phase 3 → AD-3/9/10; Phase 4 → AD-6 (кроме streaming/Function Calling/retry — F4, F2); Phase 5 → AD-7; Phase 6 → AD-8 частично (F2); Phase 7 → AD-10/11 + deferred monitoring. §15 covered через AD-12 + seed `docs/` (перечень deliverables не продублирован — допустимо, spine binds docs/architecture/*). Мелкие пропуски: cache_time/лимиты длины inline (§10, F8), chosen_inline_result (§10 optional, F9). |
| Все измерения altitude решены/deferred/open, особенно operational envelope | **частично** | Deployment & environments: топология deferred обоснованно, но изоляция DEV/PROD (бот-токены, БД, профили) не решена и не поставлена как open question (F3). Infra/provider strategy: решена (AD-5/6/7). Operations: verification деплоя есть (AD-11), rollback-механизм и tagging не зафиксированы (F7); backup/retention БД и restart policy не упомянуты — допустимо для runbook-уровня, но стоит явный deferred-пункт. |

---

## 2. Находки

### F1 — High — Идемпотентность не предотвращает дубли генераций
- **Location:** AD-3 (Rule), AD-8; таблица Capability Map (Phase 3/6).
- **Problem:** Заявленный Prevents AD-3 — «дубли генераций при повторных updates». Правило фиксирует unique только по `telegram_update_id`. Но повторное нажатие кнопки Generate Answer, ретрай callback или повторный inline_query — это **новые** updates с новыми id. Два callback с разными `update_id` по одному `inline_message_id` создадут два AiRequest → две платные генерации и два `editMessageText` в одно сообщение. Для публичного бота с бюджетом (AD-10) это реальная финансовая дивергенция между юнитами Phase 2/3/6.
- **Fix:** Дополнить AD-3: не более одного активного AiRequest (CREATED/GENERATING) на `inline_message_id` — partial unique index в миграции + проверка в application-impl; повторный callback по тому же `inline_message_id` возвращает существующий запрос (или безопасный ответ), а не создаёт новый. Зафиксировать это как часть правила, а не отдавать на усмотрение stories.

### F2 — High — Async-генерация: recovery, timeout, retry не зафиксированы
- **Location:** AD-8, AD-3 (статус EXPIRED без владельца/условия), Deferred «Async: in-process executor vs брокер [ASSUMPTION]».
- **Problem:** initSpec Phase 6 требует «timeout and retry handling», §8 — «Timeouts. Retry policies». Spine нигде не фиксирует: (a) что происходит с AiRequest в GENERATING при рестарте/падении процесса (in-process executor теряет очередь); (b) кто и по какому TTL переводит в EXPIRED/FAILED; (c) таймаут генерации и политика ретраев LLM/search (какие ошибки retryable, сколько попыток, как ретраи взаимодействуют с бюджетом AD-10). Phase 3 (persistence/lifecycle) и Phase 6 (async) собираются независимо — без этих правил они разойдутся, а [ASSUMPTION] в Deferred не даёт контракта.
- **Fix:** Добавить в AD-8 (или новый AD) правило: при старте приложения все GENERATING старше configurable timeout → FAILED (recovery sweep); generation timeout и retry policy (retryable-коды, max attempts, backoff) — конфигурируемы через `smartbot.*`; EXPIRED присваивается только application-impl. Либо явно gate: «ADR async-recovery до начала Phase 6», но тогда Phase 3 не может финализировать lifecycle-схему — предпочтительно первое.

### F3 — High — Operational envelope: изоляция DEV/PROD не решена и не open
- **Location:** Deferred «DEV/PROD топология (один VPS или разные)», AD-11 (develop → DEV, release → PROD), AD-5.
- **Problem:** Отложить топологию до Phase B — легитимно. Но spine не фиксирует даже инвариант изоляции: при long polling **один bot token не может опрашиваться двумя окружениями одновременно** (getUpdates конкурентно забирает updates; initSpec §4.3 — взаимоисключение polling/webhook). Если DEV и PROD соберутся независимо с одним токеном на одном VPS — прод-бот будет терять updates. Также не решены: отдельные БД/схемы для DEV/PROD,именование профилей (`application-dev.yml`/`application-prod.yml`), отдельные Infisical environments. Это измерение «deployment & environments», которым altitude владеет, — сейчас оно не decided, не deferred-with-invariant и не open question.
- **Fix:** Добавить в AD-11 инвариант: DEV и PROD используют разные bot tokens (разные боты) либо иное гарантированно неконфликтующее разделение; разные БД и Infisical environments; профили `dev`/`prod`. Топологию (один VPS/разные) оставить deferred, но инвариант зафиксировать сейчас. Внести в Open Questions: «DEV-бот: отдельный token/username?».

### F4 — Medium — Контракт порта LlmProvider не зафиксирован (streaming, Function Calling)
- **Location:** AD-6, AD-7, Consistency Conventions (Naming).
- **Problem:** initSpec §8 требует «Streaming where supported», «Function Calling where supported»; §9 допускает Function Calling для search tools. Форма порта `LlmProvider` (синхронный completion? streaming-вариант? поддержка tool-calls в контракте?) нигде не решена и не deferred. application-api (Phase 1/3) и adapter-llm (Phase 4) — разные юниты; без формы порта они разойдутся, и AD-6 «ядро видит только порт LlmProvider» не предотвращает эту дивергенцию.
- **Fix:** Зафиксировать в AD-6 минимальный контракт порта: синхронный `complete(...)` с usage-метриками; streaming и function-calling — либо явные optional-возможности порта (capability flags), либо явный deferred «не в initial release» с ADR-gate до Phase 5 (tool calling нужен для AUTO search). Решение «where supported» должно быть решено на уровне spine, а не story.

### F5 — Medium — AD-10 не согласован с графом AD-2: механизм enforcement лимитов не зафиксирован
- **Location:** AD-10 (Binds: infrastructure, application-impl), AD-2 (граф: нет ребра IMPL → INFRA), Deferred «Redis».
- **Problem:** Лимиты (rate limit, quota, budget) binds к application-impl и infrastructure, но impl не может зависеть от infrastructure по AD-2. Допустимый путь — порт в application-api, реализуемый infrastructure (ребро INFRA → API есть), — не назван. Два юнита могут выбрать разные схемы (порт+вызов из use case vs interceptor в adapter-telegram vs фильтр в app), и лимиты окажутся в разных местах с разной семантикой. additionally Redis deferred без дефолта: in-memory vs Redis rate limiter — тоже точка расхождения.
- **Fix:** В AD-10 явно: порты лимитов (`RateLimiter`, `QuotaService`, `BudgetGuard` — имена условны) живут в application-api, реализация — в infrastructure, enforcement — в application-impl до вызова LlmProvider; дефолт initial release — in-memory (single instance, согласовано с async [ASSUMPTION]), Redis — только по обоснованию.

### F6 — Medium — AD-1/AD-2: enforcement неполон (pom не проверяется, граф неполон)
- **Location:** AD-1 (Rule), AD-2 (mermaid + примечание «полный список — в pom и docs»).
- **Problem:** (a) ArchUnit проверяет зависимости классов, но не запрещает лишние зависимости в pom (неиспользуемый Spring/SDK в ядре пройдёт ArchUnit и нарушит «Domain без Spring» на уровне сборки). (b) AD-2 объявляет граф «единственной разрешённой топологией», но граф неполон (адаптеры/impl → domain/common вынесены в pom) — тест не может проверить правило, которого нет в артефакте; «новая зависимость требует изменения AD» не enforceable, пока полная матрица не в spine.
- **Fix:** Перенести полную матрицу зависимостей в AD-2 (или таблицей рядом); добавить maven-enforcer/bannedDependencies (запрет Spring/SDK в domain/api/common) как второй механизм enforcement в AD-1; ArchUnit-правила генерировать/сверять с той же матрицей.

### F7 — Medium — AD-11: tagging образов и rollback-механизм не зафиксированы
- **Location:** AD-11, Deferred (отсутствует пункт про release/rollback механику).
- **Problem:** CI workflow и deploy runbook — независимо собираемые юниты. Схема тегов GHCR (sha? latest? semver?) и механизм rollback («rollback steps» требуются initSpec §13, но не определены) не зафиксированы → расхождение между тем, что публикует CI, и тем, что откатывает runbook.
- **Fix:** В AD-11: образы тегируются git-sha (+ release-тег для release-ветки), `latest` не используется для деплоя; rollback = redeploy предыдущего известного тега + проверка по тому же verification-критерию. Детали — в runbook, но контракт здесь.

### F8 — Low — AD-8: cache_time и лимиты длины inline не зафиксированы
- **Location:** AD-8; initSpec §10 («appropriate caching settings», «length restrictions»).
- **Problem:** `is_personal=true` зафиксирован, но `cache_time` для персональных результатов и обработка лимита длины inline query / ответа (4096 символов сообщения) не упомянуты — мелкая, но реальная точка расхождения внутри adapter-telegram между stories.
- **Fix:** Конвенция в AD-8: `cache_time=0` для персональных результатов; truncate/overflow-стратегия для длинных ответов (обрезка с пометкой или продолжение) — одна на весь адаптер.

### F9 — Low — chosen_inline_result analytics не отражён
- **Location:** Capability Map / Deferred; initSpec §10 (optional).
- **Problem:** Возможность спеки (опциональная аналитика chosen_inline_result) не decided и не deferred — формально «пропущенное измерение», хоть и optional.
- **Fix:** Строка в Deferred: «chosen_inline_result analytics — optional, после Phase 7» либо явное «out of scope initial release».

### F10 — Low — smartbot-common без правила содержимого
- **Location:** Structural Seed, AD-1/AD-2, Conventions.
- **Problem:** «Чистая Java» — единственное ограничение. Классический дрейф: всё, что неудобно класть в domain, стекается в common, и через него обходятся границы (common становится свалкой с семантикой домена).
- **Fix:** Правило в AD-1 или Conventions: common — только технические утилиты/типы без доменной семантики; доменные типы — только в domain; ArchUnit-правило на содержимое пакетов common (запрет зависимостей от domain).

### F11 — Low — Версии: drift skeleton 3.5.7 vs spine 4.2.0; PostgreSQL 16
- **Location:** Stack; .memlog (version).
- **Problem:** Сами версии spine подтверждены проверкой 2026-11-20 (Boot 4.2.0, ArchUnit 1.5.1, WireMock 3.13.2, Testcontainers via BOM) — критерий выполнен. Но bootstrap skeleton ещё на 3.5.7 (memlog): до bump spine и реальность расходятся, и Phase 1-юниты могут стартовать на старой линии. PostgreSQL 16-alpine при актуальной 18 — допустимо (поддерживается), но выбор не обоснован.
- **Fix:** Зафиксировать bump до 4.2.0 как gate Phase 1 (задача уже есть в memlog — сослаться из spine/epic); добавить однострочное обоснование PG 16 (или bump до 17/18 — решение дешёвое сейчас, до миграций).

### F12 — Low — Порт исходящего Telegram не назван
- **Location:** AD-8, Consistency Conventions (Naming), AD-2.
- **Problem:** Async-генерация в application-impl обязана вызывать `editMessageText`/`answerCallbackQuery` через порт (прямой вызов adapter запрещён AD-2), но имя/размещение этого outbound-порта не зафиксировано. Дивергенция ограничена графом, но naming-конвенция покрывает адаптеры, не порты.
- **Fix:** Назвать порт в AD-8 (например `TelegramNotifier`/`InlineMessageGateway` в application-api) — одна строка, снимает вопрос у stories Phase 6.

---

## 3. Что сделано хорошо (для баланса)

- AD-5: VPN на сетевом уровне + приложение VPN-агностично + «протокол не предполагается» + запрет менять существующую VPN-инфраструктуру — точно закрывает главный инфраструктурный риск спеки и корректно defer'ит вариант маршрута.
- AD-6: конкретные, проверяемые факты GigaChat (base URL, endpoint, RqUID, thread-safe refresh, Минцифры truststore, модели через API) — образцовый уровень детализации для feature-altitude.
- AD-4/AD-9/AD-12: секреты, ID/миграции, ADR-дисциплина — enforceable и соответствуют §11–12, §15–16.
- Deferred-позиции в основном честные: каждая привязана к фазе/данным, которые её закроют; [ASSUMPTION] помечены явно.
- Capability Map покрывает все фазы 0–7 и даёт трассируемость epic → AD.

## 4. Итог

**pass-with-fixes.** Обязательные правки до передачи spine в epics/stories: **F1, F2, F3** (High) и **F4, F5, F6, F7** (Medium). Low-находки (F8–F12) желательны, но не блокируют. После правок spine закрывает все измерения своей altitude, включая operational envelope.
