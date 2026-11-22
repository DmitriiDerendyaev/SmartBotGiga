package ru.smartbot.adapter.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-отображение таблицы users (V2__users.sql). Маппинг domain↔JPA живёт
 * только в adapter-persistence; в domain JPA запрещён (AD-1).
 *
 * <p>Запись — только native upsert {@link SpringDataUserRepository} (AD-17);
 * сущность используется для чтения и schema validate (ddl-auto=validate).
 * id генерирует БД (gen_random_uuid(), pgcrypto из V1).
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "telegram_user_id", nullable = false, updatable = false)
    private long telegramUserId;

    @Column(name = "username")
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "language_code")
    private String languageCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** ER-форма: lowercase 'active'/'blocked'; маппинг в UserStatus — в адаптере. */
    @Column(name = "status", nullable = false)
    private String status;

    protected UserJpaEntity() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public String getStatus() {
        return status;
    }
}
