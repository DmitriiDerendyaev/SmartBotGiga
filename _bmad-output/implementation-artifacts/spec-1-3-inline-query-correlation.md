---
title: 'Story 1.3 — Inline query и персистентная корреляция'
type: 'feature'
created: '2026-11-22'
status: 'done'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Запрос пользователя нигде не хранится между inline_query и callback; без персистентной корреляции, ledger и state machine невозможны идемпотентность, переживание рестарта и одна генерация на публикацию (FR4, FR18, FR19; AD-13/14/15).

**Approach:** Миграция V3 (ai_requests, processed_updates), доменная state machine AiRequestStatus с CAS, порты репозиториев и HandleInlineQueryUseCase в application-api, реализация в impl: ledger-дедупликация → ensure user → reuse в окне TTL → создание AiRequest CREATED с opaque token.

## Boundaries & Constraints

**Always:** UUID внутри, BIGINT telegram_update_id; граф переходов CREATED→GENERATING|EXPIRED|FAILED, GENERATING→COMPLETED|FAILED|EXPIRED, терминальные immutable — enforced domain-методом; переходы в persistence — атомарный CAS (UPDATE … WHERE status=:expected); partial unique «один активный (CREATED/GENERATING) AiRequest на inline_message_id»; ledger processed_updates для ВСЕХ типов updates; correlation_token — opaque server-issued ≤64 байт, формат/генерация только в impl; query_text в БД идентичен будущему тексту LLM; повторный inline_query (тот же пользователь+текст, окно TTL configurable) переиспользует запись; blocked-проверка до создания AiRequest (EnsureUserUseCase из 1.2); MessageLimits — константы протокола в application-api (4096 сообщение, 256 query, 64 байт callback_data), используются impl и adapter; валидация длины query — отказ VALIDATION/MESSAGE_TOO_LONG без обрезки; provider_id nullable (FK добавится в Epic 2 с таблицей ai_providers); domain/api без Spring/JPA.

**Ask First:** изменение графа переходов или схемы относительно ER/spine (требует amendment AD-13/14/15); изменение формата token.

**Never:** хранение query в памяти адаптера; генерация/парсинг token адаптером; обрезка query; создание всех таблиц эпика разом (ai_request_usage/searches — позже); corrective-миграции.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Новый inline_query | валидный query + principal + update_id | AiRequest CREATED, opaque token ≤64 байт, ledger записан | N/A |
| Дубль update | тот же telegram_update_id | ledger гасит: no-op, новый AiRequest не создаётся | N/A |
| Повторный query в TTL | тот же пользователь+текст, свежая CREATED-запись | возвращается существующий token | N/A |
| Повторный query вне TTL | запись старше TTL | новая запись, новый token | N/A |
| Слишком длинный query | >256 символов | отказ VALIDATION/MESSAGE_TOO_LONG, без обрезки, AiRequest не создан | DomainException с кодом |
| Blocked пользователь | status=blocked | отказ BLOCKED до создания AiRequest | DomainException BLOCKED |
| CAS-переход | ожидаемый статус совпал | атомарный переход, true | N/A |
| CAS-гонка | статус уже изменён другим | false, мутации нет | проигравший не редактирует сообщение |
| Недопустимый переход | COMPLETED→GENERATING и т.п. | domain отклоняет | IllegalStateException/DomainException |
| Пустой query | "" / пробелы | отказ VALIDATION | DomainException |

</frozen-after-approval>

## Code Map

- `smartbot-adapter-persistence/src/main/resources/db/migration/` -- V1, V2 есть; добавить `V3__ai_requests.sql` (ai_requests + processed_updates + partial unique index)
- `smartbot-domain/src/main/java/ru/smartbot/domain/` -- error/, user/ готовы; сюда `request/AiRequest.java`, `request/AiRequestStatus.java` (граф переходов)
- `smartbot-application-api/src/main/java/ru/smartbot/application/api/` -- сюда `request/AiRequestRepository.java`, `request/ProcessedUpdateRepository.java`, `request/HandleInlineQueryUseCase.java` (результат: token + признак reuse), `telegram/MessageLimits.java` (константы протокола)
- `smartbot-application-impl/src/main/java/ru/smartbot/application/impl/` -- user/EnsureUserService готов (1.2); сюда `request/HandleInlineQueryService.java`, `request/CorrelationTokenGenerator.java` (формат "g:"+32 hex UUID), `request/CorrelationProperties.java` (@ConfigurationProperties smartbot.correlation: ttl)
- `smartbot-adapter-persistence/src/main/java/ru/smartbot/adapter/persistence/` -- user/ готов; сюда `request/AiRequestJpaEntity.java`, `request/SpringDataAiRequestRepository.java`, `request/AiRequestRepositoryAdapter.java` (CAS native update), `request/ProcessedUpdateJpaEntity.java` + адаптер (INSERT ON CONFLICT DO NOTHING → boolean)
- `smartbot-application-impl/src/test/` -- EnsureUserServiceTest есть; unit-тесты state machine/token/reuse с mock-портами
- `smartbot-adapter-persistence/src/test/` -- UserRepositoryAdapterIT есть (Testcontainers pattern); IT: partial unique, ledger, CAS-конкурентность
- Read-only: ER-форма ai_requests (docs/database/er-diagram.md), AD-13/14/15/19, ErrorCode, EnsureUserUseCase (1.2)

## Tasks & Acceptance

**Execution:**
- [x] `V3__ai_requests.sql` -- ai_requests по ER (lease_expires_at, delivery_error_code, truncation-поля, correlation_token unique, provider_id nullable без FK) + processed_updates + partial unique active-per-inline_message_id -- AD-9/13/14/15/19
- [x] `smartbot-domain/.../request/AiRequestStatus.java` + `AiRequest.java` -- граф переходов в domain, терминальные immutable -- unit без Spring
- [x] `smartbot-application-api/.../request/*.java` + `telegram/MessageLimits.java` -- порты и контракт HandleInlineQueryUseCase, константы протокола -- adapter не парсит token
- [x] `smartbot-application-impl/.../request/*.java` -- ledger → ensure user → blocked → TTL reuse → create CREATED + token; валидация длины/пустого query; CorrelationProperties -- единственный владелец
- [x] `smartbot-adapter-persistence/.../request/*.java` -- JPA + CAS native update + ledger ON CONFLICT DO NOTHING -- атомарность в БД
- [x] `smartbot-domain/src/test/.../AiRequestStatusTest.java` -- все допустимые/недопустимые переходы, терминальность -- regression графа
- [x] `smartbot-application-impl/src/test/.../HandleInlineQueryServiceTest.java` -- reuse/TTL/blocked/длина/пустой query с mock-портами -- I/O-матрица
- [x] `smartbot-adapter-persistence/src/test/.../AiRequestRepositoryAdapterIT.java` -- Testcontainers: partial unique (2 активных на одно сообщение → constraint), ledger-дубль, CAS-конкурентность (2 потока, один переход) -- I/O-матрица

**Acceptance Criteria:**
- Given новый inline_query, when use case, then CREATED + token ≤64 байт; дубль update_id гасится ledger
- Given повторный query в TTL, when use case, then тот же token; вне TTL — новый
- Given 2 параллельных CAS одного перехода, when оба выполнены, then ровно один true (IT)
- Given `make verify`, when прогон, then BUILD SUCCESS, ArchUnit/enforcer зелёные

## Spec Change Log

## Verification

**Commands:**
- `make verify` -- expected: BUILD SUCCESS; unit графа/use case + IT Testcontainers (partial unique, ledger, CAS) зелёные

## Verification Results

- `make verify` → BUILD SUCCESS.
  - domain: AiRequestStatusTest 20 (весь граф × все статусы), AiRequestTest 5 (терминальность, token ≤64, lease);
  - impl: HandleInlineQueryServiceTest 6 (вся I/O-матрица: новый/дубль/blocked/reuse/TTL/MESSAGE_TOO_LONG/VALIDATION);
  - IT (Testcontainers PostgreSQL 17): 6 — partial unique «один активный на inline_message_id»,
    новый запрос после терминального, CAS-гонка 4 потока → ровно 1 победитель, ledger-дубль, TTL-reuse;
  - app smoke IT 1/1 (миграции V1–V3 применяются, контекст поднимается).

## Suggested Review Order

**Схема и граф состояний**

- V3: ai_requests + ledger + partial unique + lease/truncation-поля
  [`V3__ai_requests.sql:1`](../../smartbot-adapter-persistence/src/main/resources/db/migration/V3__ai_requests.sql#L1)
- Граф переходов и терминальность enforced в домене
  [`AiRequestStatus.java:26`](../../smartbot-domain/src/main/java/ru/smartbot/domain/request/AiRequestStatus.java#L26)
- Переходы только через transitionTo; token ≤64; lease
  [`AiRequest.java:64`](../../smartbot-domain/src/main/java/ru/smartbot/domain/request/AiRequest.java#L64)

**Оркестрация**

- ledger → ensure user → blocked → TTL-reuse → создание корреляции
  [`HandleInlineQueryService.java:60`](../../smartbot-application-impl/src/main/java/ru/smartbot/application/impl/request/HandleInlineQueryService.java#L60)
- Opaque token, формат живёт только в impl
  [`CorrelationTokenGenerator.java:16`](../../smartbot-application-impl/src/main/java/ru/smartbot/application/impl/request/CorrelationTokenGenerator.java#L16)

**Атомарность**

- CAS-переход одним UPDATE с условием по статусу
  [`SpringDataAiRequestRepository.java:44`](../../smartbot-adapter-persistence/src/main/java/ru/smartbot/adapter/persistence/request/SpringDataAiRequestRepository.java#L44)
- Ledger: INSERT ON CONFLICT DO NOTHING
  [`SpringDataProcessedUpdateRepository.java:22`](../../smartbot-adapter-persistence/src/main/java/ru/smartbot/adapter/persistence/request/SpringDataProcessedUpdateRepository.java#L22)

**Периферия**

- IT: partial unique, CAS-гонка, ledger
  [`AiRequestRepositoryAdapterIT.java:40`](../../smartbot-adapter-persistence/src/test/java/ru/smartbot/adapter/persistence/request/AiRequestRepositoryAdapterIT.java#L40)
