package ru.smartbot.adapter.persistence.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-отображение ai_requests (V3__ai_requests.sql). Маппинг domain↔JPA —
 * только в adapter-persistence (AD-1).
 */
@Entity
@Table(name = "ai_requests")
public class AiRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "telegram_update_id")
    private Long telegramUpdateId;

    @Column(name = "inline_message_id", length = 128)
    private String inlineMessageId;

    @Column(name = "correlation_token", nullable = false, length = 64, updatable = false)
    private String correlationToken;

    @Column(name = "query_text", nullable = false)
    private String queryText;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "result_text_full")
    private String resultTextFull;

    @Column(name = "result_text_sent")
    private String resultTextSent;

    @Column(name = "truncated", nullable = false)
    private boolean truncated;

    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "delivery_error_code", length = 32)
    private String deliveryErrorCode;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiRequestJpaEntity() {
        // JPA
    }

    public AiRequestJpaEntity(UUID id, UUID userId, UUID providerId, Long telegramUpdateId,
                              String inlineMessageId, String correlationToken, String queryText,
                              String status, String resultTextFull, String resultTextSent,
                              boolean truncated, String errorCode, String deliveryErrorCode,
                              Instant leaseExpiresAt, Instant createdAt, Instant completedAt) {
        this.id = id;
        this.userId = userId;
        this.providerId = providerId;
        this.telegramUpdateId = telegramUpdateId;
        this.inlineMessageId = inlineMessageId;
        this.correlationToken = correlationToken;
        this.queryText = queryText;
        this.status = status;
        this.resultTextFull = resultTextFull;
        this.resultTextSent = resultTextSent;
        this.truncated = truncated;
        this.errorCode = errorCode;
        this.deliveryErrorCode = deliveryErrorCode;
        this.leaseExpiresAt = leaseExpiresAt;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public Long getTelegramUpdateId() {
        return telegramUpdateId;
    }

    public String getInlineMessageId() {
        return inlineMessageId;
    }

    public String getCorrelationToken() {
        return correlationToken;
    }

    public String getQueryText() {
        return queryText;
    }

    public String getStatus() {
        return status;
    }

    public String getResultTextFull() {
        return resultTextFull;
    }

    public String getResultTextSent() {
        return resultTextSent;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDeliveryErrorCode() {
        return deliveryErrorCode;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
