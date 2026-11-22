package ru.smartbot.application.api.request;

import java.time.Instant;

/**
 * Порт ledger'а транспортной идемпотентности (AD-14, AD-23).
 *
 * <p>Записывается для ВСЕХ типов updates (inline_query, callback_query,
 * chosen_inline_result). Повторная доставка того же update_id — no-op.
 */
public interface ProcessedUpdateRepository {

    /**
     * Атомарно отметить update как обработанный.
     *
     * @return true — update новый и записан; false — уже был обработан (дубль)
     */
    boolean tryRecord(long telegramUpdateId, String kind, Instant now);
}
