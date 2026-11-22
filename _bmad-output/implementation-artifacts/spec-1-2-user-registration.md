---
title: 'Story 1.2 — Распознавание и автоматическая регистрация пользователя'
type: 'feature'
created: '2026-11-22'
status: 'draft'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Бот не идентифицирует пользователей: без User/регистрации невозможны лимиты, приватность и владение запросами (FR15, FR19; AD-17).

**Approach:** Миграция V2 (users, user_settings), TelegramPrincipal и порт UserRepository в application-api, атомарный get-or-create use case в application-impl (единственный владелец User), JPA-реализация в adapter-persistence; upsert не трогает status/history_enabled.

## Boundaries & Constraints

**Always:** UUID id, BIGINT telegram_user_id unique; timestamptz/Instant UTC; Flyway forward-only, ddl-auto=validate; get-or-create атомарный (INSERT … ON CONFLICT (telegram_user_id) DO UPDATE только profile-полей и last_seen_at); status/history_enabled upsert НЕ трогает (history_enabled default false); blocked меняется только явным use case; проверка blocked — до создания AiRequest (контракт фиксируется сейчас, вызов — в story 1.3); адаптер не регистрирует сам, передаёт TelegramPrincipal; domain/api без Spring/JPA (enforcer); миграция только нужных таблиц (users, user_settings).

**Ask First:** изменение схемы users/user_settings относительно ER (docs/database/er-diagram.md); добавление полей сверх минимизации AD-26.

**Never:** регистрация в адаптере; upsert status/history_enabled; soft-delete; реальные персональные данные в тестах; JPA-сущности в domain (маппинг в persistence-адаптере).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Первый update | TelegramPrincipal нового пользователя | User создан (active), UserSettings созданы (history_enabled=false) | N/A |
| Повторный update | существующий telegram_user_id | обновлены только profile-поля и last_seen_at; id/status/settings не изменены | N/A |
| Blocked пользователь | status=blocked, повторный update | upsert не разблокирует; EnsureUserUseCase возвращает признак blocked | отказ BLOCKED (ErrorCode из story 1.1) |
| Гонка регистрации | 2 параллельных первых update одного telegram_user_id | ровно один User; оба получают один id | constraint не падает наружу |
| Минимизация | principal без username/firstName | User создан с null-полями | N/A |

</frozen-after-approval>

## Code Map

- `smartbot-adapter-persistence/src/main/resources/db/migration/V1__baseline.sql` -- существует (pgcrypto); добавить `V2__users.sql`
- `smartbot-domain/src/main/java/ru/smartbot/domain/` -- error/ готов (1.1); сюда `user/User.java`, `user/UserSettings.java`, `user/UserStatus.java` (чистая Java)
- `smartbot-application-api/src/main/java/ru/smartbot/application/api/` -- error/ готов; сюда `user/TelegramPrincipal.java` (record), `user/UserRepository.java` (порт), `user/EnsureUserUseCase.java` (входящий контракт: principal → UserView/registered+blocked)
- `smartbot-application-impl/src/main/java/ru/smartbot/application/impl/` -- сюда `user/EnsureUserService.java` (@Service, единственный владелец User; spring-context/tx разрешены)
- `smartbot-adapter-persistence/src/main/java/ru/smartbot/adapter/persistence/` -- сюда `user/UserJpaEntity.java`, `user/UserSettingsJpaEntity.java`, `user/SpringDataUserRepository.java`, `user/UserRepositoryAdapter.java` (маппинг domain↔JPA; upsert native query ON CONFLICT)
- `smartbot-adapter-persistence/pom.xml` -- testcontainers-postgresql/junit-jupiter уже подключены
- `smartbot-app/src/test/.../ArchitectureBoundariesTest.java` -- границы уже enforced; новые классы должны им соответствовать
- Read-only: ER-форма users/user_settings (docs/database/er-diagram.md), AD-17/26, ErrorCode (story 1.1)

## Tasks & Acceptance

**Execution:**
- [ ] `V2__users.sql` -- users + user_settings по ER: UUID PK, telegram_user_id BIGINT unique, status, history_enabled default false, индексы/FK -- AD-9/17/26
- [ ] `smartbot-domain/.../user/*.java` -- User, UserSettings, UserStatus (active/blocked) -- чистое ядро
- [ ] `smartbot-application-api/.../user/*.java` -- TelegramPrincipal record, UserRepository порт (getOrCreate, findById…), EnsureUserUseCase контракт -- adapter передаёт principal
- [ ] `smartbot-application-impl/.../user/EnsureUserService.java` -- атомарный get-or-create через порт; blocked-признак в результате -- единственный владелец
- [ ] `smartbot-adapter-persistence/.../user/*.java` -- JPA-сущности + native upsert (ON CONFLICT DO UPDATE только profile/last_seen_at) + адаптер порта -- маппинг вне domain
- [ ] `smartbot-adapter-persistence/src/test/.../UserRepositoryAdapterIT.java` -- Testcontainers PostgreSQL: все строки I/O-матрицы, включая concurrency-тест (2 потока, один telegram_user_id) -- regression
- [ ] `smartbot-application-impl/src/test/.../EnsureUserServiceTest.java` -- unit с mock-портом: blocked не разблокируется, профиль обновляется -- без Spring-контекста

**Acceptance Criteria:**
- Given первый update, when EnsureUserUseCase, then User+UserSettings созданы, history_enabled=false
- Given параллельные первые update, when оба обработаны, then ровно один User (IT на Testcontainers)
- Given blocked, when повторный update, then status не изменён, результат несёт blocked-признак
- Given `make verify`, when полный прогон, then BUILD SUCCESS, ArchUnit/enforcer зелёные

## Spec Change Log

## Verification

**Commands:**
- `make verify` -- expected: BUILD SUCCESS; IT с Testcontainers PostgreSQL зелёные (docker.sock проброшен в Makefile); concurrency-тест проходит
