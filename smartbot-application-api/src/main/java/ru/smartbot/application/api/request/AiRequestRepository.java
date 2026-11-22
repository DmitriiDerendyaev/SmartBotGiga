package ru.smartbot.application.api.request;

import ru.smartbot.domain.request.AiRequest;
import ru.smartbot.domain.request.AiRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт репозитория AiRequest (outbound, AD-3, AD-13…AD-15).
 *
 * <p>Единственный владелец состояния — application-impl; адаптеры только
 * вызывают порт. Переходы статусов — атомарный CAS по ожидаемому статусу
 * ({@code UPDATE … WHERE status = :expected}); возвращает false, если
 * CAS проигран (AD-15).
 */
public interface AiRequestRepository {

    /** Сохранить новый запрос (CREATED). */
    AiRequest create(AiRequest request);

    Optional<AiRequest> findById(UUID id);

    Optional<AiRequest> findByCorrelationToken(String correlationToken);

    /**
     * Активный (CREATED/GENERATING) запрос пользователя с тем же текстом,
     * созданный не раньше {@code createdAfter} — для TTL-reuse (AD-13).
     */
    Optional<AiRequest> findActiveByUserAndQuery(UUID userId, String queryText, Instant createdAfter);

    /** Активный запрос по опубликованному сообщению (бизнес-unique, AD-14). */
    Optional<AiRequest> findActiveByInlineMessageId(String inlineMessageId);

    /**
     * Привязать inline_message_id к активному запросу (первый callback).
     *
     * @return true — привязано; false — уже занято другим активным запросом
     *         (partial unique) или статус неактивен
     */
    boolean attachInlineMessageId(UUID id, String inlineMessageId);

    /**
     * Атомарный CAS-переход статуса.
     *
     * @return true, если переход применён; false — статус уже другой (гонка)
     */
    boolean transition(UUID id, AiRequestStatus expected, AiRequestStatus target, Instant now);
}
