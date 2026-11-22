package ru.smartbot.domain.request;

import ru.smartbot.domain.error.ErrorCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Запрос к AI (AD-3, AD-9, AD-13…AD-15, AD-19): чистая Java, без JPA/Spring.
 *
 * <p>Единственный владелец состояния — application-impl. Переход статусов —
 * только через {@link #transitionTo}: граф enforced доменом, терминальные
 * статусы immutable. В persistence переход выполняется атомарным CAS.
 *
 * <p>Поля: query_text идентичен тексту, уходящему в LLM (без обрезки);
 * correlation_token — opaque ≤64 байт; результат хранится в двух формах
 * (полный и отправленный) + флаг truncation (AD-19).
 */
public record AiRequest(
        UUID id,
        UUID userId,
        UUID providerId,
        Long telegramUpdateId,
        String inlineMessageId,
        String correlationToken,
        String queryText,
        AiRequestStatus status,
        String resultTextFull,
        String resultTextSent,
        boolean truncated,
        ErrorCode errorCode,
        ErrorCode deliveryErrorCode,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant completedAt) {

    public AiRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(correlationToken, "correlationToken");
        Objects.requireNonNull(queryText, "queryText");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (correlationToken.length() > 64) {
            throw new IllegalArgumentException("correlation_token > 64 байт (AD-13)");
        }
    }

    /**
     * Переход в новый статус по графу AD-15.
     *
     * @param target целевой статус
     * @param now    момент перехода (UTC)
     * @return новое состояние; терминальный статус перезаписать нельзя
     * @throws IllegalStateException недопустимый переход (включая мутацию терминального)
     */
    public AiRequest transitionTo(AiRequestStatus target, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Недопустимый переход AiRequest " + id + ": " + status + " → " + target);
        }
        Instant completed = target.isTerminal() ? now : completedAt;
        return new AiRequest(id, userId, providerId, telegramUpdateId, inlineMessageId,
                correlationToken, queryText, target, resultTextFull, resultTextSent, truncated,
                errorCode, deliveryErrorCode, leaseExpiresAt, createdAt, completed);
    }

    /** Пометить ошибку (для FAILED/EXPIRED/delivery); код безопасен (AD-20). */
    public AiRequest withError(ErrorCode code) {
        return new AiRequest(id, userId, providerId, telegramUpdateId, inlineMessageId,
                correlationToken, queryText, status, resultTextFull, resultTextSent, truncated,
                code, deliveryErrorCode, leaseExpiresAt, createdAt, completedAt);
    }

    /** Пометить ошибку доставки: генерация успешна, доставка — нет (AD-15). */
    public AiRequest withDeliveryError(ErrorCode code) {
        return new AiRequest(id, userId, providerId, telegramUpdateId, inlineMessageId,
                correlationToken, queryText, status, resultTextFull, resultTextSent, truncated,
                errorCode, code, leaseExpiresAt, createdAt, completedAt);
    }

    /** {окно} lease истёк — кандидат recovery/watchdog (AD-15). */
    public boolean isLeaseExpired(Instant now) {
        return leaseExpiresAt != null && leaseExpiresAt.isBefore(now);
    }
}
