package ru.smartbot.application.api.telegram;

/**
 * Бюджеты длины Telegram (AD-19): знание протокола, экспортируется адаптером
 * и используется application-impl при формировании запроса/финального текста.
 *
 * <p>Финальный текст собирает impl: тело обрезается, sources-блок с URL —
 * никогда; adapter отправляет as-is. Валидация query — отказ без обрезки.
 */
public final class MessageLimits {

    /** Максимум символов текста сообщения Telegram (AD-19). */
    public static final int MAX_MESSAGE_LENGTH = 4096;

    /** Лимит длины inline query (Telegram); сверх — отказ MESSAGE_TOO_LONG. */
    public static final int MAX_QUERY_LENGTH = 256;

    /** Максимум callback_data, байт (opaque token, AD-13). */
    public static final int MAX_CALLBACK_DATA_BYTES = 64;

    private MessageLimits() {
    }
}
