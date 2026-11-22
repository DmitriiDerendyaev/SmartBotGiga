package ru.smartbot.application.impl.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.application.api.request.HandleCallbackUseCase;
import ru.smartbot.application.api.request.ProcessedUpdateRepository;
import ru.smartbot.application.api.user.EnsureUserUseCase;
import ru.smartbot.application.api.user.UserView;
import ru.smartbot.domain.error.DomainException;
import ru.smartbot.domain.error.ErrorCode;
import ru.smartbot.domain.request.AiRequest;
import ru.smartbot.domain.request.AiRequestStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Валидация callback перед генерацией (AD-8, AD-13, AD-14, AD-15, AD-17).
 *
 * <p>Проверки: ledger-дубль → регистрация пользователя → blocked → наличие
 * inline_message_id → запись по opaque token → владелец → TTL → статус.
 * EXPIRED ставит только impl (CAS). Повторное нажатие по активному запросу
 * идемпотентно (reusable=true) — второй платной генерации не будет.
 */
@Service
public class HandleCallbackService implements HandleCallbackUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandleCallbackService.class);

    private final ProcessedUpdateRepository processedUpdates;
    private final EnsureUserUseCase ensureUser;
    private final AiRequestRepository aiRequests;
    private final SmartBotRequestProperties properties;

    public HandleCallbackService(ProcessedUpdateRepository processedUpdates,
                                 EnsureUserUseCase ensureUser,
                                 AiRequestRepository aiRequests,
                                 SmartBotRequestProperties properties) {
        this.processedUpdates = Objects.requireNonNull(processedUpdates, "processedUpdates");
        this.ensureUser = Objects.requireNonNull(ensureUser, "ensureUser");
        this.aiRequests = Objects.requireNonNull(aiRequests, "aiRequests");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        Objects.requireNonNull(command, "command");
        Instant now = Instant.now();

        if (!processedUpdates.tryRecord(command.telegramUpdateId(), "callback_query", now)) {
            log.debug("Дубль callback update_id={} погашен ledger'ом", command.telegramUpdateId());
            return new Result(null, null, true, false);
        }

        EnsureUserUseCase.Result user = ensureUser.ensureUser(command.principal());
        if (user.blocked()) {
            throw new DomainException(ErrorCode.BLOCKED);
        }

        if (isBlank(command.inlineMessageId())) {
            throw new DomainException(ErrorCode.INVALID_CALLBACK);
        }
        if (isBlank(command.correlationToken())) {
            throw new DomainException(ErrorCode.INVALID_CALLBACK);
        }

        AiRequest request = aiRequests.findByCorrelationToken(command.correlationToken())
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_CALLBACK));

        UserView userView = user.user();
        if (request.userId() != null && !request.userId().equals(userView.id())) {
            throw new DomainException(ErrorCode.INVALID_CALLBACK);
        }

        // Первый callback привязывает опубликованное сообщение (partial unique, AD-14)
        if (request.inlineMessageId() == null) {
            if (!aiRequests.attachInlineMessageId(request.id(), command.inlineMessageId())) {
                throw new DomainException(ErrorCode.INVALID_CALLBACK);
            }
        } else if (!request.inlineMessageId().equals(command.inlineMessageId())) {
            throw new DomainException(ErrorCode.INVALID_CALLBACK);
        }

        Duration ttl = properties.getCallbackTtl();
        if (expired(request, ttl, now)) {
            aiRequests.transition(request.id(), request.status(), AiRequestStatus.EXPIRED, now);
            throw new DomainException(ErrorCode.EXPIRED);
        }

        return switch (request.status()) {
            case CREATED, GENERATING -> new Result(request.id(), request.status(), false, true);
            case COMPLETED -> new Result(request.id(), AiRequestStatus.COMPLETED, false, true);
            case EXPIRED -> throw new DomainException(ErrorCode.EXPIRED);
            case FAILED -> new Result(request.id(), AiRequestStatus.FAILED, false, true);
        };
    }

    private static boolean expired(AiRequest request, Duration ttl, Instant now) {
        return request.status().isActive()
                && request.createdAt().plus(ttl).isBefore(now);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
