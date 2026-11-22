---
title: 'Story 1.2 — Распознавание и автоматическая регистрация пользователя'
type: 'feature'
created: '2026-11-22'
status: 'done'
review_loop_iteration: 0
baseline_commit: '83e384e07b90de9c045733bb5822555658cd27ff'
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
- [x] `V2__users.sql` -- users + user_settings по ER: UUID PK, telegram_user_id BIGINT unique, status, history_enabled default false, индексы/FK -- AD-9/17/26
- [x] `smartbot-domain/.../user/*.java` -- User, UserSettings, UserStatus (active/blocked) -- чистое ядро
- [x] `smartbot-application-api/.../user/*.java` -- TelegramPrincipal record, UserRepository порт (getOrCreate, findById…), EnsureUserUseCase контракт -- adapter передаёт principal
- [x] `smartbot-application-impl/.../user/EnsureUserService.java` -- атомарный get-or-create через порт; blocked-признак в результате -- единственный владелец
- [x] `smartbot-adapter-persistence/.../user/*.java` -- JPA-сущности + native upsert (ON CONFLICT DO UPDATE только profile/last_seen_at) + адаптер порта -- маппинг вне domain
- [x] `smartbot-adapter-persistence/src/test/.../UserRepositoryAdapterIT.java` -- Testcontainers PostgreSQL: все строки I/O-матрицы, включая concurrency-тест (2 потока, один telegram_user_id) -- regression
- [x] `smartbot-application-impl/src/test/.../EnsureUserServiceTest.java` -- unit с mock-портом: blocked не разблокируется, профиль обновляется -- без Spring-контекста

**Acceptance Criteria:**
- Given первый update, when EnsureUserUseCase, then User+UserSettings созданы, history_enabled=false
- Given параллельные первые update, when оба обработаны, then ровно один User (IT на Testcontainers)
- Given blocked, when повторный update, then status не изменён, результат несёт blocked-признак
- Given `make verify`, when полный прогон, then BUILD SUCCESS, ArchUnit/enforcer зелёные

## Spec Change Log

## Verification

**Commands:**
- `make verify` -- expected: BUILD SUCCESS; IT с Testcontainers PostgreSQL зелёные (docker.sock проброшен в Makefile); concurrency-тест проходит

## Verification Results

- `make verify` → BUILD SUCCESS. persistence IT 6/6 (Testcontainers PostgreSQL 17),
  application-impl unit 5/5, app smoke IT 1/1 (composition root + Flyway + реальные порты).
- Inline-review (subagents недоступны из-за лимита модели) выявил и закрыл:
  1) HIGH: composition root не сканировал entities/repositories вне `ru.smartbot.app`
     → добавлены `@EntityScan("ru.smartbot")` / `@EnableJpaRepositories("ru.smartbot")`
     и smoke-IT, который это ловит;
  2) build: `spring-boot:repackage` заменял основной jar жирным, из-за чего failsafe
     терял `target/classes` и component scan не видел бины → fat jar переведён на
     `classifier=exec` (Dockerfile обновлён);
  3) Boot 4: `@SpringBootApplication` больше не несёт `@SpringBootConfiguration`,
     `@EntityScan` переехал в `spring-boot.persistence.autoconfigure`,
     Flyway-autoconfiguration — отдельный артефакт `spring-boot-flyway`;
  4) ArchUnit: ложное срабатывание `ru.smartbot.app` ⊂ `ru.smartbot.application`
     → точная проверка пакета.

## Suggested Review Order

**Миграция и домен**

- V2: users/user_settings, unique telegram_user_id, history_enabled=false
  [`V2__users.sql:1`](../../smartbot-adapter-persistence/src/main/resources/db/migration/V2__users.sql#L1)
- Неизменяемая доменная модель пользователя
  [`User.java:18`](../../smartbot-domain/src/main/java/ru/smartbot/domain/user/User.java#L18)

**Порт и владелец**

- Контракт get-or-create (адаптер не регистрирует сам)
  [`UserRepository.java:20`](../../smartbot-application-api/src/main/java/ru/smartbot/application/api/user/UserRepository.java#L20)
- Единственный владелец User в impl
  [`EnsureUserService.java:28`](../../smartbot-application-impl/src/main/java/ru/smartbot/application/impl/user/EnsureUserService.java#L28)

**Атомарность в БД**

- Native upsert: только profile/last_seen_at, xmax=0 → created
  [`SpringDataUserRepository.java:38`](../../smartbot-adapter-persistence/src/main/java/ru/smartbot/adapter/persistence/user/SpringDataUserRepository.java#L38)
- Маппинг domain↔JPA и транзакция
  [`UserRepositoryAdapter.java:29`](../../smartbot-adapter-persistence/src/main/java/ru/smartbot/adapter/persistence/user/UserRepositoryAdapter.java#L29)

**Периферия**

- Testcontainers IT: гонка регистрации, blocked, неприкосновенность настроек
  [`UserRepositoryAdapterIT.java:40`](../../smartbot-adapter-persistence/src/test/java/ru/smartbot/adapter/persistence/user/UserRepositoryAdapterIT.java#L40)
- Composition root: entity/repo scan + smoke IT
  [`SmartBotApplication.java:22`](../../smartbot-app/src/main/java/ru/smartbot/app/SmartBotApplication.java#L22)
