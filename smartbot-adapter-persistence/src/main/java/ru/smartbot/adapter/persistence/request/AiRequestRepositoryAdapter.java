package ru.smartbot.adapter.persistence.request;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.domain.error.ErrorCode;
import ru.smartbot.domain.request.AiRequest;
import ru.smartbot.domain.request.AiRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Реализация порта {@link AiRequestRepository} (AD-3, AD-13…AD-15): маппинг
 * domain↔JPA и атомарный CAS статуса в БД.
 */
@Repository
public class AiRequestRepositoryAdapter implements AiRequestRepository {

    private final SpringDataAiRequestRepository repository;
    private final TransactionTemplate requiresNew;

    public AiRequestRepositoryAdapter(SpringDataAiRequestRepository repository,
                                      PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.requiresNew = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        // Конфликт partial unique не должен помечать rollback-only внешнюю транзакцию.
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public AiRequest create(AiRequest request) {
        Objects.requireNonNull(request, "request");
        AiRequestJpaEntity saved = repository.saveAndFlush(toEntity(request));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiRequest> findById(UUID id) {
        return repository.findById(id).map(AiRequestRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiRequest> findByCorrelationToken(String correlationToken) {
        return repository.findByCorrelationToken(correlationToken)
                .map(AiRequestRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiRequest> findActiveByUserAndQuery(UUID userId, String queryText,
                                                        Instant createdAfter) {
        List<AiRequestJpaEntity> rows = repository.findReusable(userId, queryText, createdAfter);
        return rows.isEmpty() ? Optional.empty() : Optional.of(toDomain(rows.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiRequest> findActiveByInlineMessageId(String inlineMessageId) {
        List<AiRequestJpaEntity> rows = repository.findActiveByInlineMessageId(inlineMessageId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(toDomain(rows.get(0)));
    }

    @Override
    public boolean attachInlineMessageId(UUID id, String inlineMessageId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inlineMessageId, "inlineMessageId");
        Boolean attached = requiresNew.execute(status -> {
            try {
                return repository.attachInlineMessageId(id, inlineMessageId) == 1;
            } catch (DataIntegrityViolationException e) {
                // partial unique: сообщение уже занято другим активным запросом (AD-14)
                status.setRollbackOnly();
                return false;
            }
        });
        return Boolean.TRUE.equals(attached);
    }

    @Override
    @Transactional
    public boolean transition(UUID id, AiRequestStatus expected, AiRequestStatus target, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        return repository.compareAndSetStatus(id, expected.name(), target.name(), now) == 1;
    }

    private static AiRequestJpaEntity toEntity(AiRequest r) {
        return new AiRequestJpaEntity(
                r.id(), r.userId(), r.providerId(), r.telegramUpdateId(), r.inlineMessageId(),
                r.correlationToken(), r.queryText(), r.status().name(),
                r.resultTextFull(), r.resultTextSent(), r.truncated(),
                r.errorCode() == null ? null : r.errorCode().name(),
                r.deliveryErrorCode() == null ? null : r.deliveryErrorCode().name(),
                r.leaseExpiresAt(), r.createdAt(), r.completedAt());
    }

    private static AiRequest toDomain(AiRequestJpaEntity e) {
        return new AiRequest(
                e.getId(), e.getUserId(), e.getProviderId(), e.getTelegramUpdateId(),
                e.getInlineMessageId(), e.getCorrelationToken(), e.getQueryText(),
                AiRequestStatus.valueOf(e.getStatus()),
                e.getResultTextFull(), e.getResultTextSent(), e.isTruncated(),
                parseCode(e.getErrorCode()), parseCode(e.getDeliveryErrorCode()),
                e.getLeaseExpiresAt(), e.getCreatedAt(), e.getCompletedAt());
    }

    private static ErrorCode parseCode(String persisted) {
        return persisted == null ? null : ErrorCode.valueOf(persisted);
    }
}
