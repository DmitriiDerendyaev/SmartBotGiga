# ER Diagram — SmartBot

> Статус: зафиксировано Architecture Spine (AD-9, AD-14…AD-19, AD-26).
> Формы ниже входят в **первую миграцию Phase 3** (corrective-миграции запрещены).
> UUID — внутренние идентификаторы; BIGINT — Telegram ID. Контекст диалогов не хранится.

```mermaid
erDiagram
    USERS ||--o| USER_SETTINGS : "has"
    USERS ||--o{ AI_REQUESTS : "makes"
    AI_PROVIDERS ||--o{ AI_REQUESTS : "served by"
    SEARCH_PROVIDERS ||--o{ AI_REQUEST_SEARCHES : "performed by"
    AI_REQUESTS ||--o{ AI_REQUEST_SEARCHES : "uses"
    AI_REQUESTS ||--o{ AI_REQUEST_USAGE : "per-call"

    USERS {
        uuid id PK
        bigint telegram_user_id UK "BIGINT, unique (AD-17)"
        varchar username "опционально, минимизация (AD-26)"
        varchar first_name
        varchar language_code
        timestamptz created_at
        timestamptz last_seen_at
        varchar status "active/blocked; upsert НЕ трогает (AD-17)"
    }
    USER_SETTINGS {
        uuid id PK
        uuid user_id FK,UK
        varchar search_mode "NONE/NATIVE/EXTERNAL/AUTO; default из global"
        uuid preferred_provider_id FK
        boolean history_enabled "default OFF (AD-26)"
        timestamptz updated_at
    }
    AI_REQUESTS {
        uuid id PK
        uuid user_id FK
        uuid provider_id FK
        bigint telegram_update_id "id callback-update, создавшего запрос (AD-3)"
        varchar inline_message_id "partial unique: один активный на сообщение (AD-14)"
        varchar correlation_token UK "opaque, <=64 байт, server-issued (AD-13)"
        text query_text "идентичен тексту, ушедшему в LLM (AD-19)"
        varchar status "CREATED/GENERATING/COMPLETED/FAILED/EXPIRED (AD-15)"
        text result_text_full "полный, до обрезки (AD-19)"
        text result_text_sent "отправленная версия (AD-19)"
        boolean truncated
        varchar error_code "ErrorCode enum (AD-20)"
        varchar delivery_error_code "отдельная ось доставки (AD-15)"
        timestamptz lease_expires_at "heartbeat исполнителя (AD-15)"
        timestamptz created_at
        timestamptz completed_at
    }
    AI_PROVIDERS {
        uuid id PK
        varchar code UK "gigachat, ..."
        varchar display_name
        varchar base_url
        varchar default_model "обязан входить в verified models (AD-24)"
        jsonb config "БЕЗ секретов; verified models, maxOutputTokens cap"
        boolean enabled
    }
    SEARCH_PROVIDERS {
        uuid id PK
        varchar code UK
        varchar display_name
        jsonb config "БЕЗ секретов!"
        boolean enabled
    }
    AI_REQUEST_SEARCHES {
        uuid id PK
        uuid request_id FK
        uuid search_provider_id FK
        text search_query
        jsonb normalized_results "каноническая форма SearchResult (AD-7)"
        timestamptz created_at
    }
    AI_REQUEST_USAGE {
        uuid id PK
        uuid request_id FK "unique(request_id, call_index) (AD-16)"
        int call_index "per-call гранулярность; UK на request_id НЕТ"
        int prompt_tokens
        int completion_tokens
        int total_tokens
        numeric estimated_cost "считает только impl (smartbot.pricing.*)"
        int latency_ms "длительность LLM-call (из adapter)"
    }
    PROCESSED_UPDATES {
        bigint telegram_update_id PK "ledger ВСЕХ updates (AD-14)"
        varchar kind "inline_query/callback_query/chosen_inline_result"
        timestamptz processed_at
    }
```

## Индексы и constraints (обязательны в миграции)

- `users.telegram_user_id` — unique (AD-17).
- `ai_requests.correlation_token` — unique (AD-13).
- **Partial unique**: один активный (CREATED/GENERATING) AiRequest на
  `inline_message_id` (AD-14):
  `CREATE UNIQUE INDEX … ON ai_requests (inline_message_id) WHERE status IN ('CREATED','GENERATING') AND inline_message_id IS NOT NULL`.
- `ai_request_usage (request_id, call_index)` — unique (AD-16).
- `ai_requests.user_id + created_at`, FK-индексы, `lease_expires_at` (recovery sweep).
- `processed_updates.telegram_update_id` — PK (AD-14).

## Принципы

- Секреты провайдеров **не в БД**: конфигурация отдельно, credentials в Infisical.
- История: `history_enabled` default **off**; при включении — retention TTL
  (дефолт 30 дней) + автоочистка; `/delete_me` — реальное удаление (AD-26).
- Статусы меняет только application-impl, атомарный CAS (AD-15).
