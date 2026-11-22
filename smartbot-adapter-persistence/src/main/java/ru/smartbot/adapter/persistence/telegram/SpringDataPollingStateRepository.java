package ru.smartbot.adapter.persistence.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Spring Data репозиторий offset'а long polling (AD-23).
 */
public interface SpringDataPollingStateRepository extends JpaRepository<TelegramPollingStateJpaEntity, Short> {

    /** Монотонный offset: никогда не уменьшается (рестарт/повтор). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE telegram_polling_state
               SET last_update_id = GREATEST(last_update_id, :lastUpdateId),
                   updated_at = :now
             WHERE id = 1
            """)
    int saveMonotonic(@Param("lastUpdateId") long lastUpdateId, @Param("now") Instant now);
}
