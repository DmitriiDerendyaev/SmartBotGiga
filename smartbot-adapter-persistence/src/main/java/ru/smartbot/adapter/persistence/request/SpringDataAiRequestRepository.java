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
 * Spring Data репозиторий ai_requests (AD-13…AD-15, AD-19).
 *
 * <p>Все переходы — атомарные UPDATE с условием по ожидаемому статусу;
 * проигравший CAS получает 0 строк (AD-15).
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

    @Query("""
            select r from AiRequestJpaEntity r
            where r.inlineMessageId = :inlineMessageId
              and r.status in ('CREATED', 'GENERATING')
            order by r.createdAt desc
            limit 1
            """)
    List<AiRequestJpaEntity> findActiveByInlineMessageId(@Param("inlineMessageId") String inlineMessageId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ai_requests
               SET inline_message_id = :inlineMessageId
             WHERE id = :id
               AND inline_message_id IS NULL
               AND status IN ('CREATED', 'GENERATING')
            """)
    int attachInlineMessageId(@Param("id") UUID id, @Param("inlineMessageId") String inlineMessageId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ai_requests
               SET lease_expires_at = :leaseExpiresAt
             WHERE id = :id AND status IN ('CREATED', 'GENERATING')
            """)
    int renewLease(@Param("id") UUID id, @Param("leaseExpiresAt") Instant leaseExpiresAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ai_requests
               SET status = 'COMPLETED',
                   result_text_full = :fullText,
                   result_text_sent = :sentText,
                   truncated = :truncated,
                   delivery_error_code = :deliveryError,
                   completed_at = :now
             WHERE id = :id AND status = 'GENERATING'
            """)
    int complete(@Param("id") UUID id,
                 @Param("fullText") String fullText,
                 @Param("sentText") String sentText,
                 @Param("truncated") boolean truncated,
                 @Param("deliveryError") String deliveryError,
                 @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ai_requests SET delivery_error_code = :code WHERE id = :id
            """)
    int setDeliveryError(@Param("id") UUID id, @Param("code") String code);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ai_requests
               SET status = 'FAILED', error_code = :code, completed_at = :now
             WHERE id = :id AND status IN ('CREATED', 'GENERATING')
            """)
    int fail(@Param("id") UUID id, @Param("code") String code, @Param("now") Instant now);

    @Query("""
            select r from AiRequestJpaEntity r
            where r.status = 'GENERATING'
              and r.leaseExpiresAt is not null
              and r.leaseExpiresAt < :now
            """)
    List<AiRequestJpaEntity> findExpiredLeases(@Param("now") Instant now);

    @Query("""
            select r from AiRequestJpaEntity r
            where r.status in ('CREATED', 'GENERATING')
              and r.createdAt < :threshold
            """)
    List<AiRequestJpaEntity> findActiveCreatedBefore(@Param("threshold") Instant threshold);

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
