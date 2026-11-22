package ru.smartbot.adapter.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-отображение таблицы user_settings (V2__users.sql, ER-форма).
 *
 * <p>Создание при регистрации — native insert {@link SpringDataUserRepository}
 * (history_enabled=false, search_mode — DB default); upsert регистрации
 * настройки НЕ трогает (AD-17/AD-26). Сущность — для чтения и schema validate;
 * изменение настроек — явные use cases (Phase 3+).
 */
@Entity
@Table(name = "user_settings")
public class UserSettingsJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** ER-форма: NONE/NATIVE/EXTERNAL/AUTO; маппинг в SearchMode — в адаптере. */
    @Column(name = "search_mode", nullable = false)
    private String searchMode;

    /** FK на ai_providers появится вместе с таблицей провайдеров (V3+). */
    @Column(name = "preferred_provider_id")
    private UUID preferredProviderId;

    @Column(name = "history_enabled", nullable = false)
    private boolean historyEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSettingsJpaEntity() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSearchMode() {
        return searchMode;
    }

    public UUID getPreferredProviderId() {
        return preferredProviderId;
    }

    public boolean isHistoryEnabled() {
        return historyEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
