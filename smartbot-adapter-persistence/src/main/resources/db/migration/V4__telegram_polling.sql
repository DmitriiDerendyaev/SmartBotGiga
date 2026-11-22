-- Story 1.4: состояние long polling (AD-23): offset подтверждается только
-- после персистентной обработки update и переживает рестарт.
CREATE TABLE telegram_polling_state (
    id             SMALLINT PRIMARY KEY DEFAULT 1,
    last_update_id BIGINT   NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_telegram_polling_singleton CHECK (id = 1)
);

INSERT INTO telegram_polling_state (id, last_update_id) VALUES (1, 0)
    ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE telegram_polling_state IS 'Offset getUpdates (AD-23): at-least-once, переживает рестарт';
