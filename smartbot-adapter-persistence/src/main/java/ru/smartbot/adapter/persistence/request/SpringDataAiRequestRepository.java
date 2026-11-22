package ru.smartbot.adapter.persistence.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data репозиторий ai_requests (AD-13…AD-15).
 *
 * <p>CAS-переход статуса — один атомарный UPDATE с условием по ожидаемому
 * статусу; проигравший CAS получает 0 строк (AD-15).
 */
public interface SpringDataAiRequestRepository extends JpaRepository<AiRequestJpaEntity, UUID> {

    Optional<AiRequestJpaEntity> findByCorrelationToken(String correlationToken);

    @Query("""
            select r from AiRequestJpaEntity r
            where r.userId = :userId
              and r.queryText = :queryText
              and r.createdAt >= :createdAfter
              and r.status in ('CREATED', 'GENERATING')
            order by r.createdAt desc
            limit 1
            """)
    List<AiRequestJpaEntity> findReusable(@Param("userId") UUID userId,
                                          @Param("queryText") String queryText,
                                          @Param("createdAfter") Instant createdAfter);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ai_requests
               SET status = :target,
                   completed_at = CASE WHEN :target IN ('COMPLETED', 'FAILED', 'EXPIRED')
                                       THEN :now ELSE completed_at END
             WHERE id = :id AND status = :expected
            """)
    int compareAndSetStatus(@Param("id") UUID id,
                            @Param("expected") String expected,
                            @Param("target") String target,
                            @Param("now") Instant now);
}
