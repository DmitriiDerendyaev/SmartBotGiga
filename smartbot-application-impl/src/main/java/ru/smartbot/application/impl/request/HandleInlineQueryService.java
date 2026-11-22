package ru.smartbot.application.impl.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.application.api.request.HandleInlineQueryUseCase;
import ru.smartbot.application.api.request.ProcessedUpdateRepository;
import ru.smartbot.application.api.telegram.MessageLimits;
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
import java.util.UUID;

/**
 * Обработка inline_query: ledger → регистрация → blocked → TTL-reuse →
 * создание корреляции (AD-13, AD-14, AD-17, AD-19).
 *
 * <p>Единственный владелец AiRequest/User; адаптер передаёт principal и текст.
 * Query в БД идентичен тексту, который уйдёт в LLM; слишком длинный/пустой —
 * отказ без обрезки. Повторный update_id гасится ledger'ом.
 */
@Service
public class HandleInlineQueryService implements HandleInlineQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandleInlineQueryService.class);

    private final ProcessedUpdateRepository processedUpdates;
    private final EnsureUserUseCase ensureUser;
    private final AiRequestRepository aiRequests;
    private final CorrelationTokenGenerator tokenGenerator;
    private final SmartBotRequestProperties properties;

    public HandleInlineQueryService(ProcessedUpdateRepository processedUpdates,
                                    EnsureUserUseCase ensureUser,
                                    AiRequestRepository aiRequests,
                                    CorrelationTokenGenerator tokenGenerator,
                                    SmartBotRequestProperties properties) {
        this.processedUpdates = Objects.requireNonNull(processedUpdates, "processedUpdates");
        this.ensureUser = Objects.requireNonNull(ensureUser, "ensureUser");
        this.aiRequests = Objects.requireNonNull(aiRequests, "aiRequests");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    @Transactional
    public Result handle(Command command) {
        Objects.requireNonNull(command, "command");
        Instant now = Instant.now();

        if (!processedUpdates.tryRecord(command.telegramUpdateId(), "inline_query", now)) {
            log.debug("Дубль inline_query update_id={} погашен ledger'ом", command.telegramUpdateId());
            return Result.alreadyProcessed();
        }

        EnsureUserUseCase.Result user = ensureUser.ensureUser(command.principal());
        if (user.blocked()) {
            throw new DomainException(ErrorCode.BLOCKED);
        }

        String query = validate(command.queryText());
        UserView userView = user.user();

        Duration ttl = properties.getCorrelationTtl();
        Optional<AiRequest> reusable =
                aiRequests.findActiveByUserAndQuery(userView.id(), query, now.minus(ttl));
        if (reusable.isPresent()) {
            AiRequest existing = reusable.get();
            log.debug("Переиспользование корреляции requestId={} (TTL {})", existing.id(), ttl);
            return new Result(existing.id(), existing.correlationToken(), false, true);
        }

        AiRequest created = aiRequests.create(new AiRequest(
                UUID.randomUUID(),
                userView.id(),
                null,
                command.telegramUpdateId(),
                null,
                tokenGenerator.generate(),
                query,
                AiRequestStatus.CREATED,
                null, null, false,
                null, null,
                null,
                now,
                null));

        log.debug("Создана корреляция requestId={} token={}", created.id(),
                maskToken(created.correlationToken()));
        return new Result(created.id(), created.correlationToken(), false, false);
    }

    private String validate(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION);
        }
        int limit = Math.min(MessageLimits.MAX_QUERY_LENGTH, properties.getMaxQueryLength());
        if (query.length() > limit) {
            throw new DomainException(ErrorCode.MESSAGE_TOO_LONG);
        }
        return query;
    }

    /** В логах не светим полный token (он даёт право запустить генерацию). */
    private static String maskToken(String token) {
        return token.length() <= 5 ? "***" : token.substring(0, 5) + "…";
    }
}
