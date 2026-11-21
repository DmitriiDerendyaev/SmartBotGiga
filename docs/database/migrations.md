# Migrations — SmartBot

- Инструмент: **Flyway**, location: `smartbot-adapter-persistence/src/main/resources/db/migration`.
- Naming: `V<version>__<description>.sql`, только forward migrations.
- Destructive changes (drop column/table) — отдельный approval + rollback plan.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate не меняет схему.

## История

| Версия | Описание | Фаза |
|---|---|---|
| V1 | baseline: `pgcrypto` extension | 1 |
| V2+ | users, user_settings, ai_providers, search_providers, ai_requests, ai_request_searches, ai_request_usage | 3 (план) |

## Правила

1. Каждая миграция идемпотентна там, где возможно (`IF NOT EXISTS`).
2. Индексы, FK и unique constraints — в миграциях, не в коде.
3. Большие таблицы: индексы `CONCURRENTLY` где требуется (отдельная миграция без transaction).
4. Тесты миграций: Testcontainers PostgreSQL в adapter-persistence.
