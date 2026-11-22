package ru.smartbot.application.api.user;

import ru.smartbot.domain.user.UserStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-модель User для входящих контрактов (story 1.2): адаптеры и вызывающие
 * use cases получают представление, а не доменный объект и не JPA-сущность.
 *
 * <p>Profile-поля опциональны (AD-26). Время — Instant (UTC).
 */
public record UserView(
        UUID id,
        long telegramUserId,
        String username,
        String firstName,
        String languageCode,
        UserStatus status,
        Instant createdAt,
        Instant lastSeenAt) {

    public UserView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
    }
}
