package ru.smartbot.domain.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Пользователь SmartBot (AD-17, AD-26): чистая Java, без JPA/Spring.
 *
 * <p>Единственный владелец состояния — application-impl; создание/обновление —
 * только атомарный get-or-create через порт {@code UserRepository}. Profile-поля
 * (username/firstName/languageCode) опциональны — минимизация данных (AD-26).
 * Идентификатор внутри — UUID; Telegram id — BIGINT (AD-9). Время — Instant (UTC).
 *
 * <p>Неизменяемый value-объект: повторный update возвращает новое состояние,
 * а не мутирует существующее.
 */
public record User(
        UUID id,
        long telegramUserId,
        String username,
        String firstName,
        String languageCode,
        Instant createdAt,
        Instant lastSeenAt,
        UserStatus status) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        Objects.requireNonNull(status, "status");
        if (telegramUserId <= 0) {
            throw new IllegalArgumentException("telegramUserId должен быть положительным");
        }
    }

    /** Признак blocked для проверки до создания AiRequest (AD-17). */
    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }
}
