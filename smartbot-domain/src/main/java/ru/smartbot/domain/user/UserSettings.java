package ru.smartbot.domain.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Настройки пользователя (AD-26, ER-форма user_settings): чистая Java.
 *
 * <p>Создаются вместе с User при регистрации: {@code historyEnabled=false}
 * (история по умолчанию выключена), {@code searchMode} — global default.
 * Upsert регистрации настройки НЕ трогает; изменение — только явными
 * use cases (UpdateUserSettingsUseCase, Phase 3+).
 *
 * <p>{@code preferredProviderId} опционален (FK на ai_providers появится
 * вместе с таблицей провайдеров, V3+).
 */
public record UserSettings(
        UUID id,
        UUID userId,
        SearchMode searchMode,
        UUID preferredProviderId,
        boolean historyEnabled,
        Instant updatedAt) {

    public UserSettings {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(searchMode, "searchMode");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
