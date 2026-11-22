# Epic 1 Context: Мгновенный inline-отклик

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Полный vertical slice SmartBot: пользователь набирает `@SmartBot <запрос>` в любом чате и мгновенно получает лёгкий результат с кнопкой **Generate Answer**; нажатие запускает mock-генерацию и обновляет опубликованное сообщение. Цепочка: Telegram adapter (WireMock) → use cases → PostgreSQL, с авторегистрацией, идемпотентностью и безопасными ошибками. Реальная верификация Telegram/GigaChat отложена до Epic 5 (Phase 0 gate); локально — только mock/WireMock/Testcontainers.

## Stories

- Story 1.1: Безопасные ошибки и redaction логов
- Story 1.2: Распознавание и автоматическая регистрация пользователя
- Story 1.3: Inline query и персистентная корреляция
- Story 1.4: Telegram polling и мгновенный лёгкий результат
- Story 1.5: Приём и валидация callback
- Story 1.6: Mock-генерация и обновление сообщения

## Requirements & Constraints

- Публичный inline-доступ из любого чата без добавления бота; каждый запрос независим, контекст диалога не хранится и не используется.
- Мгновенный `InlineQueryResultArticle` (`is_personal=true`, `cache_time=0`) с кнопкой Generate Answer на опубликованном inline-сообщении; выбор публикует ответ в чат. `answerCallbackQuery` — немедленно, до генерации.
- Регистрация на первом любом update: атомарный get-or-create; upsert трогает только profile-поля и `last_seen_at`, не трогает `status`/`history_enabled` (history по умолчанию off); blocked меняется только явным use case; проверка blocked — до создания AiRequest.
- Query хранится только в БД (TTL configurable); повторный inline_query (тот же текст+пользователь, окно TTL) переиспользует запись. Текст в БД идентичен уходящему в LLM; валидация длины — отказ (MESSAGE_TOO_LONG/VALIDATION) без обрезки; дополнительный cap `smartbot.limits.max-query-length` — до LLM.
- Идемпотентность: ledger `processed_updates` для всех типов updates; не более одного активного (CREATED/GENERATING) AiRequest на `inline_message_id` (partial unique + проверка в impl); повторный callback идемпотентно возвращает существующий запрос — одна публикация = один расход.
- Валидация callback: запись существует, принадлежит `callback.from.id`, не истекла, статус допускает генерацию. Forged/чужой/истёкший/без `inline_message_id` → доменные коды INVALID_CALLBACK/EXPIRED, безопасный alert, без падений; EXPIRED ставит только impl.
- Генерация: CAS CREATED→GENERATING→COMPLETED, lease/heartbeat исполнителя; ошибка доставки = COMPLETED + отдельный `delivery_error_code`; «message is not modified» = успех; recovery при старте (осиротевший GENERATING с истёкшим lease) → FAILED RESTARTED; watchdog TTL → EXPIRED; проигравший CAS не редактирует сообщение.
- Безопасность: наружу только ErrorCode + типизированные безопасные параметры и текст из единого каталога; сырые внешние тела/заголовки не пересекают порт. Секреты — только env/Infisical, не в коде/БД/логах. Logback masking (bot token `bot\d+:[A-Za-z0-9_-]+`, Authorization, PrivateKey) во всех appender'ах; wire-логи WARN; PII не на INFO.
- Бюджеты длины: `MessageLimits` (4096 символов сообщения, лимит query, callback_data ≤64 байт) — знание adapter-telegram, экспортируется и используется impl. Финальный текст целиком собирает impl (обрезка тела; sources-блок не обрезается никогда), adapter отправляет as-is; `parse_mode=HTML` на весь проект, экранирование в adapter; в БД полный текст + отправленная версия/флаг truncation.
- Транспорт at-least-once: offset getUpdates подтверждается только после персистентной обработки (ledger) и персистится; graceful shutdown дожидается in-flight; один bot token = ровно один poller. Приложение VPN-агностично — маршрут Telegram на сетевом уровне (Epic 5), VPN-логики в коде нет.
- Тесты: WireMock (base URL Telegram конфигурируем), Testcontainers PostgreSQL (concurrency регистрации, дубли, гонки watchdog), unit ядра без Spring, ArchUnit-стражи; сборка через Docker (`make verify`).

## Technical Decisions

- Hexagonal modular monolith, 10 Maven-модулей (Phase 1 skeleton готов и verified); границы enforced ArchUnit + maven-enforcer; domain/application-api без Spring/SDK; wiring только в composition root; зависимости строго по матрице модулей.
- Единый владелец состояния — application-impl (AiRequest, User/UserSettings) через порт репозитория; адаптеры передают команды/DTO и `TelegramPrincipal`, не мутируют домен и не регистрируют пользователя.
- Граф `AiRequestStatus` в domain: CREATED → GENERATING | EXPIRED | FAILED; GENERATING → COMPLETED | FAILED | EXPIRED; терминальные immutable; переходы — атомарный CAS (`UPDATE … WHERE status = :expected`).
- Корреляция: `callback_data` — только opaque token ≤64 байт; формат/генерация/валидация в impl/api, adapter передаёт непрозрачно. Контракт `HandleCallbackUseCase(token, inlineMessageId, principal)` фиксируется в application-api; mock и реальная генерация используют один контракт.
- Порты в application-api: `LlmProvider` — синхронный `complete(LlmRequest): LlmResponse` + capability flags; запрос несёт model, messages, maxOutputTokens, optional tools; ответ — text, toolCalls, finishReason, usage (per-call, возвращается ядру; adapter ничего не персистит). Function Calling в контракте, streaming deferred. Epic 1 — MockLlmProvider (static response). `InlineMessageEditor` (editMessageText по inline_message_id) — реализация adapter-telegram.
- Transport-retry (429 retry_after, сетевые) — только внутри telegram-адаптера; application-impl не ретраит. Исполнитель генерации — in-process + lease/recovery (брокер не нужен); Redis — только через ADR.
- Единый enum `ErrorCode` в domain: VALIDATION, INVALID_CALLBACK, EXPIRED, BLOCKED, RATE_LIMITED, QUOTA_EXCEEDED, BUDGET_EXCEEDED, TIMEOUT, PROVIDER_UNAVAILABLE, AUTH, SEARCH_UNAVAILABLE, DELIVERY_FAILED, MESSAGE_TOO_LONG, RESTARTED, INTERNAL. Единственная таблица ErrorCode→безопасный текст в application-api/impl; adapter не маппит ошибки сам и не использует `getMessage()` для пользователя; тест-страж запрещает String message из адаптера в user-facing путях.
- PostgreSQL 17 + Flyway forward-only, `ddl-auto=validate`; UUID внутри, BIGINT Telegram id, timestamptz/Instant (UTC); индексы/FK/unique в миграциях. Миграции per-story: V2 (users, user_settings), V3 (ai_requests, processed_updates). Формы ledger/partial unique/lease/delivery_error_code/truncation фиксируются до первой миграции — corrective-миграции запрещены.
- Конфигурация `smartbot.*` (@ConfigurationProperties): TTL, lease, timeouts конфигурируемы, дефолты консервативные. Naming: порты — интерфейсы в api, use cases `<Action>UseCase`, адаптеры `<Tech><Port>`; IT — `*IT.java`. Документация на русском, англ. термины сохраняются; отчёт в конце фазы.

## UX & Interaction Patterns

Стандартный Telegram inline UI, кастомного дизайна нет. Flow: `@SmartBot запрос` → мгновенный article-результат с кнопкой Generate Answer → пользователь публикует результат в чат → нажатие кнопки → немедленный ack callback → сообщение обновляется сгенерированным (пока mock) ответом. Ошибки — только безопасные alert'ы из каталога; истёкшие/чужие/подделанные кнопки обрабатываются gracefully, без падений.

## Cross-Story Dependencies

- Внутри эпика: каталог ErrorCode (1.1) используется всеми stories; регистрация (1.2, миграция V2) предшествует созданию AiRequest (1.3, V3); корреляция и схема (1.3) нужны polling (1.4) и callback (1.5); генерация (1.6) опирается на контракт 1.5 и порты LlmProvider/InlineMessageEditor.
- Epic 2 заменяет mock реальным GigaChat через те же порты и контракт HandleCallbackUseCase; Epic 4 встраивает порты лимитов в существующие use cases. Реальная Telegram/GigaChat-верификация — Epic 5 (gate: данные инфраструктуры от пользователя). Forward-зависимостей нет: эпик автономен на mock.