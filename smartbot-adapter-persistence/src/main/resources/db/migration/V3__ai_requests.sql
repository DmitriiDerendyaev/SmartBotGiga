-- Story 1.3: ai_requests + processed_updates (AD-13, AD-14, AD-15, AD-19).
-- Только таблицы story: ai_request_usage/searches/providers — позже (Epic 2/3).
-- UUID — внутренние id; BIGINT telegram_update_id — id callback-update (AD-3);
-- correlation_token — opaque server-issued ≤64 байт (AD-13).
-- Формы lease/recovery/truncation фиксируются здесь до первой миграции (AD-9/15/19).

CREATE TABLE ai_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL,
    -- FK на ai_providers появится вместе с таблицей провайдеров (Epic 2).
    provider_id         UUID,
    telegram_update_id  BIGINT,
    inline_message_id   VARCHAR(128),
    correlation_token   VARCHAR(64)  NOT NULL,
    query_text          TEXT         NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'CREATED',
    result_text_full    TEXT,
    result_text_sent    TEXT,
    truncated           BOOLEAN      NOT NULL DEFAULT FALSE,
    error_code          VARCHAR(32),
    delivery_error_code VARCHAR(32),
    -- lease/heartbeat исполнителя (AD-15): осиротевший GENERATING → FAILED RESTARTED
    lease_expires_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT uq_ai_requests_correlation_token UNIQUE (correlation_token),
    CONSTRAINT fk_ai_requests_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_ai_requests_status CHECK (
        status IN ('CREATED', 'GENERATING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    CONSTRAINT chk_ai_requests_correlation_token_len CHECK (char_length(correlation_token) <= 64)
);

-- Бизнес-идемпотентность (AD-14): не более одного активного запроса на публикацию.
CREATE UNIQUE INDEX uq_ai_requests_active_inline_message
    ON ai_requests (inline_message_id)
    WHERE status IN ('CREATED', 'GENERATING') AND inline_message_id IS NOT NULL;

-- Поиск активного запроса для TTL-reuse (AD-13): пользователь + текст.
CREATE INDEX idx_ai_requests_user_query
    ON ai_requests (user_id, created_at DESC);

-- Recovery sweep (AD-15) и watchdog: активные с истёкшим lease.
CREATE INDEX idx_ai_requests_status_lease
    ON ai_requests (status, lease_expires_at);

CREATE INDEX idx_ai_requests_user_id ON ai_requests (user_id);
CREATE INDEX idx_ai_requests_telegram_update_id ON ai_requests (telegram_update_id);

-- Транспортная идемпотентность (AD-14): ledger ВСЕХ типов updates.
CREATE TABLE processed_updates (
    telegram_update_id BIGINT PRIMARY KEY,
    kind               VARCHAR(32) NOT NULL,
    processed_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE processed_updates IS 'Ledger update_id для at-least-once доставки (AD-14/23)';
COMMENT ON COLUMN ai_requests.correlation_token IS 'Opaque server-issued token, adapter не парсит (AD-13)';
