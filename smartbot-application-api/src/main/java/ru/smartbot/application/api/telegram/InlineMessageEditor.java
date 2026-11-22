package ru.smartbot.application.api.telegram;

/**
 * Исходящий порт обновления опубликованного inline-сообщения (AD-8, AD-23).
 *
 * <p>Реализация — adapter-telegram. Текст формирует application-impl целиком
 * (обрезка тела, sources-блок не обрезается — AD-19); адаптер отправляет
 * as-is и не переформатирует. «message is not modified» считается успехом;
 * «message not found» → {@code DELIVERY_FAILED}.
 */
public interface InlineMessageEditor {

    /**
     * @param inlineMessageId опубликованное сообщение
     * @param finalText       готовый HTML-текст от impl
     * @return результат доставки (генерация и доставка — разные оси, AD-15)
     */
    DeliveryResult edit(String inlineMessageId, String finalText);

    enum DeliveryResult {
        DELIVERED,
        NOT_MODIFIED,
        MESSAGE_NOT_FOUND,
        FAILED;

        /** Доставка успешна (включая идемпотентный повтор). */
        public boolean delivered() {
            return this == DELIVERED || this == NOT_MODIFIED;
        }
    }
}
