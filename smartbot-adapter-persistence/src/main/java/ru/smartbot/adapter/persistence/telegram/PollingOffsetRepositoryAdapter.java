package ru.smartbot.adapter.persistence.telegram;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.smartbot.application.api.telegram.PollingOffsetRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Реализация порта offset'а (AD-23): монотонное сохранение в БД.
 */
@Repository
public class PollingOffsetRepositoryAdapter implements PollingOffsetRepository {

    private final SpringDataPollingStateRepository repository;

    public PollingOffsetRepositoryAdapter(SpringDataPollingStateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalLong lastUpdateId() {
        return repository.findById((short) 1)
                .map(state -> OptionalLong.of(state.getLastUpdateId()))
                .orElseGet(OptionalLong::empty);
    }

    @Override
    @Transactional
    public void save(long lastUpdateId) {
        repository.saveMonotonic(lastUpdateId, Instant.now());
    }
}
