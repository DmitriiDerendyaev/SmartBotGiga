package ru.smartbot.adapter.persistence.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA-отображение processed_updates — ledger всех updates (AD-14).
 */
@Entity
@Table(name = "processed_updates")
public class ProcessedUpdateJpaEntity {

    @Id
    @Column(name = "telegram_update_id", nullable = false, updatable = false)
    private Long telegramUpdateId;

    @Column(name = "kind", nullable = false, length = 32)
    private String kind;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedUpdateJpaEntity() {
        // JPA
    }

    public ProcessedUpdateJpaEntity(Long telegramUpdateId, String kind, Instant processedAt) {
        this.telegramUpdateId = telegramUpdateId;
        this.kind = kind;
        this.processedAt = processedAt;
    }

    public Long getTelegramUpdateId() {
        return telegramUpdateId;
    }

    public String getKind() {
        return kind;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
