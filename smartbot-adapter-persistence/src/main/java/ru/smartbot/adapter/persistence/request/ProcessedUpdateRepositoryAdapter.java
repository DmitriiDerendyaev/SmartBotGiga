package ru.smartbot.adapter.persistence.request;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.smartbot.application.api.request.ProcessedUpdateRepository;
import ru.smartbot.domain.error.DomainException;
import ru.smartbot.domain.error.ErrorCode;

import java.time.Instant;
import java.util.Objects;

/**
 * Реализация ledger'а (AD-14): атомарный INSERT … ON CONFLICT DO NOTHING.
 */
@Repository
public class ProcessedUpdateRepositoryAdapter implements ProcessedUpdateRepository {

    private final SpringDataProcessedUpdateRepository repository;

    public ProcessedUpdateRepositoryAdapter(SpringDataProcessedUpdateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional
    public boolean tryRecord(long telegramUpdateId, String kind, Instant now) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(now, "now");
        int inserted = repository.tryInsert(telegramUpdateId, kind, now);
        if (inserted > 1) {
            throw new DomainException(ErrorCode.INTERNAL);
        }
        return inserted == 1;
    }
}
