package ru.smartbot.adapter.persistence.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Spring Data репозиторий ledger'а processed_updates (AD-14).
 */
public interface SpringDataProcessedUpdateRepository extends JpaRepository<ProcessedUpdateJpaEntity, Long> {

    /** Атомарная запись: дубль update_id — 0 строк. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO processed_updates (telegram_update_id, kind, processed_at)
            VALUES (:updateId, :kind, :now)
            ON CONFLICT (telegram_update_id) DO NOTHING
            """)
    int tryInsert(@Param("updateId") long updateId,
                  @Param("kind") String kind,
                  @Param("now") Instant now);
}
