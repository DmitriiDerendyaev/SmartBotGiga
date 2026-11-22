package ru.smartbot.adapter.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA репозиторий users + native upsert регистрации (AD-17).
 *
 * <p>Upsert атомарен на уровне БД: {@code INSERT … ON CONFLICT (telegram_user_id)
 * DO UPDATE} обновляет ТОЛЬКО profile-поля и last_seen_at; status не трогается
 * (blocked не разблокируется). id генерирует БД (gen_random_uuid()).
 */
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByTelegramUserId(long telegramUserId);

    /**
     * Атомарный get-or-create. Признак created: {@code (xmax = 0)} — системный
     * столбец PostgreSQL равен 0 только у вставленной строки; у обновлённой
     * (DO UPDATE) там xid текущей транзакции. Параллельные первые upsert'ы
     * одного telegram_user_idсериализуются unique-индексом: ровно один INSERT.
     *
     * <p>Aliases в кавычках — camelCase для interface-проекции {@link UserRow}.
     */
    @Query(nativeQuery = true, value = """
            INSERT INTO users (telegram_user_id, username, first_name, language_code, created_at, last_seen_at, status)
            VALUES (:telegramUserId, :username, :firstName, :languageCode, :now, :now, 'active')
            ON CONFLICT (telegram_user_id) DO UPDATE
            SET username = EXCLUDED.username,
                first_name = EXCLUDED.first_name,
                language_code = EXCLUDED.language_code,
                last_seen_at = EXCLUDED.last_seen_at
            RETURNING id AS "id",
                      telegram_user_id AS "telegramUserId",
                      username AS "username",
                      first_name AS "firstName",
                      language_code AS "languageCode",
                      created_at AS "createdAt",
                      last_seen_at AS "lastSeenAt",
                      status AS "status",
                      (xmax = 0) AS "created"
            """)
    UserRow upsert(@Param("telegramUserId") long telegramUserId,
                   @Param("username") String username,
                   @Param("firstName") String firstName,
                   @Param("languageCode") String languageCode,
                   @Param("now") Instant now);

    /**
     * Идемпотентное создание настроек при регистрации: history_enabled=false
     * (AD-26), search_mode/preferred_provider_id — DB defaults (ER).
     * Повторный вызов (в т.ч. параллельный) — DO NOTHING: настройки,
     * включая history_enabled и updated_at, не изменяются (AD-17).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO user_settings (user_id, history_enabled, updated_at)
            VALUES (:userId, FALSE, :now)
            ON CONFLICT (user_id) DO NOTHING
            """)
    int insertSettingsIfAbsent(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Проекция строки upsert (timestamptz → Instant, UTC). */
    interface UserRow {

        UUID getId();

        Long getTelegramUserId();

        String getUsername();

        String getFirstName();

        String getLanguageCode();

        Instant getCreatedAt();

        Instant getLastSeenAt();

        String getStatus();

        Boolean getCreated();
    }
}
