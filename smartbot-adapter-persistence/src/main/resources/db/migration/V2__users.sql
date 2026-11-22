-- Story 1.2: users + user_settings (docs/database/er-diagram.md; AD-9, AD-17, AD-26).
-- Миграция создаёт только таблицы story; ai_requests/ai_providers/ledger — V3+.
-- UUID — внутренние id (gen_random_uuid(), pgcrypto из V1); BIGINT — Telegram id.
-- Все временные поля — timestamptz (UTC).

CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_user_id BIGINT       NOT NULL,
    username         VARCHAR(255),
    first_name       VARCHAR(255),
    language_code    VARCHAR(35),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- active/blocked (ER); upsert регистрации статус НЕ трогает (AD-17),
    -- blocked меняет только явный use case application-impl.
    status           VARCHAR(16)  NOT NULL DEFAULT 'active',
    CONSTRAINT uq_users_telegram_user_id UNIQUE (telegram_user_id),
    CONSTRAINT chk_users_status CHECK (status IN ('active', 'blocked'))
);

COMMENT ON COLUMN users.status IS 'active/blocked; меняется только явным use case (AD-17), upsert не трогает';

CREATE TABLE user_settings (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID        NOT NULL,
    -- NONE/NATIVE/EXTERNAL/AUTO (AD-7); DB default зеркалит global default
    -- smartbot.search.mode=NONE (application.yml).
    search_mode           VARCHAR(16) NOT NULL DEFAULT 'NONE',
    -- FK на ai_providers будет добавлен вместе с таблицей ai_providers (V3+):
    -- story 1.2 создаёт только users/user_settings.
    preferred_provider_id UUID,
    -- История по умолчанию выключена (AD-26); upsert регистрации не трогает.
    history_enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_settings_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_settings_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_settings_search_mode CHECK (search_mode IN ('NONE', 'NATIVE', 'EXTERNAL', 'AUTO'))
);

-- Индексы: users.telegram_user_id — unique (AD-17), user_settings.user_id — FK+UK (ER);
-- оба покрыты unique-constraint'ами выше (дополнительные индексы не нужны).
