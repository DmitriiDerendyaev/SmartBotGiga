package ru.smartbot.application.api.user;

/**
 * Идентификация пользователя Telegram (AD-17, docs/api/contracts.md).
 *
 * <p>Адаптер передаёт principal в каждый use case и НЕ регистрирует
 * пользователя сам: регистрация — атомарный get-or-create в application-impl.
 * Profile-поля опциональны (минимизация данных, AD-26): principal без
 * username/firstName/languageCode валиден.
 *
 * @param telegramUserId BIGINT Telegram id, положительный
 * @param username       опционально (без '@')
 * @param firstName      опционально
 * @param languageCode   опционально (IETF language tag из Telegram)
 */
public record TelegramPrincipal(
        long telegramUserId,
        String username,
        String firstName,
        String languageCode) {

    public TelegramPrincipal {
        if (telegramUserId <= 0) {
            throw new IllegalArgumentException("telegramUserId должен быть положительным");
        }
    }
}
