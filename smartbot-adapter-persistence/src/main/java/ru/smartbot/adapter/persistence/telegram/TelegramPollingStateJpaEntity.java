package ru.smartbot.adapter.persistence.telegram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA-отображение singleton-состояния long polling (V4).
 */
@Entity
@Table(name = "telegram_polling_state")
public class TelegramPollingStateJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "last_update_id", nullable = false)
    private long lastUpdateId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TelegramPollingStateJpaEntity() {
        // JPA
    }

    public Short getId() {
        return id;
    }

    public long getLastUpdateId() {
        return lastUpdateId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
