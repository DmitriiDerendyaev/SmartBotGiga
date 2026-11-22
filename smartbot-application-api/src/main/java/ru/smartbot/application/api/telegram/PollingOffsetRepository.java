package ru.smartbot.application.api.telegram;

import java.util.OptionalLong;

/**
 * Порт хранения offset long polling (AD-23).
 *
 * <p>Offset подтверждается только после персистентной обработки update
 * (ledger, AD-14) и сохраняется, чтобы рестарт не проигрывал старые updates.
 */
public interface PollingOffsetRepository {

    /** Последний подтверждённый offset (update_id + 1); пусто — ещё не было. */
    OptionalLong lastUpdateId();

    /** Атомарно сохранить offset (не уменьшая). */
    void save(long lastUpdateId);
}
