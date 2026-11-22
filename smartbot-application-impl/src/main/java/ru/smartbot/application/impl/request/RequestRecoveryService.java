package ru.smartbot.application.impl.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.domain.error.ErrorCode;
import ru.smartbot.domain.request.AiRequest;
import ru.smartbot.domain.request.AiRequestStatus;

import java.time.Instant;
import java.util.List;

/**
 * Recovery и watchdog (AD-15).
 *
 * <p>Recovery при старте: осиротевшие GENERATING с истёкшим lease (in-process
 * executor потерял задачу при деплое) → FAILED RESTARTED. Watchdog: активные
 * запросы старше TTL → EXPIRED. Единственный писатель статусов — application-impl.
 */
@Service
public class RequestRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RequestRecoveryService.class);

    private final AiRequestRepository aiRequests;
    private final SmartBotRequestProperties properties;

    public RequestRecoveryService(AiRequestRepository aiRequests, SmartBotRequestProperties properties) {
        this.aiRequests = aiRequests;
        this.properties = properties;
    }

    /** При старте: не оставляем вечно «активные» осиротевшие генерации. */
    @EventListener(ContextRefreshedEvent.class)
    public void recoverOnStartup() {
        List<AiRequest> orphans = aiRequests.findExpiredLeases(Instant.now());
        for (AiRequest orphan : orphans) {
            aiRequests.fail(orphan.id(), ErrorCode.RESTARTED, Instant.now());
        }
        if (!orphans.isEmpty()) {
            log.warn("Recovery: {} осиротевших GENERATING переведены в FAILED RESTARTED", orphans.size());
        }
    }

    /** Watchdog: активные дольше TTL → EXPIRED (проигравший CAS ничего не делает). */
    @Scheduled(fixedDelayString = "${smartbot.generation.watchdog-interval:PT1M}")
    public void expireStale() {
        Instant threshold = Instant.now().minus(properties.getCallbackTtl());
        for (AiRequest stale : aiRequests.findActiveCreatedBefore(threshold)) {
            if (stale.status() == AiRequestStatus.GENERATING) {
                aiRequests.fail(stale.id(), ErrorCode.TIMEOUT, Instant.now());
            } else {
                aiRequests.transition(stale.id(), AiRequestStatus.CREATED,
                        AiRequestStatus.EXPIRED, Instant.now());
            }
        }
    }
}
