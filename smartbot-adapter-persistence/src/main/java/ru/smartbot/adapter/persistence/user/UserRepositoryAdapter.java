package ru.smartbot.adapter.persistence.user;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.application.api.user.UserRepository;
import ru.smartbot.domain.user.User;
import ru.smartbot.domain.user.UserStatus;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Реализация порта {@link UserRepository} (AD-17): атомарный native upsert
 * + маппинг domain↔JPA (в domain JPA запрещён, AD-1).
 *
 * <p>Адаптер не регистрирует пользователя сам: вход — TelegramPrincipal,
 * владелец регистрации — application-impl (EnsureUserService). Upsert трогает
 * только profile-поля и last_seen_at; status/history_enabled не изменяются.
 * UserSettings создаются идемпотентно в той же транзакции (history_enabled=false).
 */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository repository;

    public UserRepositoryAdapter(SpringDataUserRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional
    public Registration getOrCreate(TelegramPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        Instant now = Instant.now();

        SpringDataUserRepository.UserRow row = repository.upsert(
                principal.telegramUserId(),
                principal.username(),
                principal.firstName(),
                principal.languageCode(),
                now);
        repository.insertSettingsIfAbsent(row.getId(), now);

        return new Registration(toDomain(row), Boolean.TRUE.equals(row.getCreated()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return repository.findById(id).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByTelegramUserId(long telegramUserId) {
        return repository.findByTelegramUserId(telegramUserId).map(UserRepositoryAdapter::toDomain);
    }

    private static User toDomain(SpringDataUserRepository.UserRow row) {
        return new User(
                row.getId(),
                row.getTelegramUserId(),
                row.getUsername(),
                row.getFirstName(),
                row.getLanguageCode(),
                row.getCreatedAt(),
                row.getLastSeenAt(),
                toStatus(row.getStatus()));
    }

    private static User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getTelegramUserId(),
                entity.getUsername(),
                entity.getFirstName(),
                entity.getLanguageCode(),
                entity.getCreatedAt(),
                entity.getLastSeenAt(),
                toStatus(entity.getStatus()));
    }

    /** ER-форма хранит lowercase active/blocked; доменный enum — ACTIVE/BLOCKED. */
    private static UserStatus toStatus(String persistedValue) {
        return UserStatus.valueOf(persistedValue.toUpperCase(Locale.ROOT));
    }
}
