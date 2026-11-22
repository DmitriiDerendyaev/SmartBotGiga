package ru.smartbot.application.impl.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.smartbot.application.api.llm.LlmProvider;
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.application.api.request.GenerateAnswerUseCase;
import ru.smartbot.application.api.telegram.InlineMessageEditor;
import ru.smartbot.domain.error.ErrorCode;
import ru.smartbot.domain.request.AiRequest;
import ru.smartbot.domain.request.AiRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Асинхронная генерация (AD-8, AD-15, AD-18, AD-19, AD-21).
 *
 * <p>CAS CREATED→GENERATING + lease (один in-flight исполнитель), вызов
 * LlmProvider, сборка финального текста (impl), CAS GENERATING→COMPLETED,
 * затем editMessageText. Проигравший CAS не редактирует сообщение.
 * Ошибка доставки фиксируется отдельно от результата генерации.
 */
@Service
public class GenerateAnswerService implements GenerateAnswerUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateAnswerService.class);

    private final AiRequestRepository aiRequests;
    private final LlmProvider llm;
    private final InlineMessageEditor editor;
    private final FinalTextBuilder textBuilder;
    private final SmartBotRequestProperties properties;
    private final Executor executor;

    public GenerateAnswerService(AiRequestRepository aiRequests,
                                 LlmProvider llm,
                                 InlineMessageEditor editor,
                                 FinalTextBuilder textBuilder,
                                 SmartBotRequestProperties properties,
                                 @Qualifier("generationExecutor") Executor executor) {
        this.aiRequests = Objects.requireNonNull(aiRequests, "aiRequests");
        this.llm = Objects.requireNonNull(llm, "llm");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.textBuilder = Objects.requireNonNull(textBuilder, "textBuilder");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public boolean start(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        Instant now = Instant.now();
        boolean claimed = aiRequests.transition(requestId, AiRequestStatus.CREATED,
                AiRequestStatus.GENERATING, now);
        if (!claimed) {
            // Уже GENerating — генерация идёт; прочие статусы не запускают новую.
            return aiRequests.findById(requestId)
                    .map(r -> r.status() == AiRequestStatus.GENERATING)
                    .orElse(false);
        }
        aiRequests.renewLease(requestId, now.plus(properties.getLeaseTtl()));
        executor.execute(() -> generate(requestId));
        return true;
    }

    /** Выполняется в отдельном потоке: без внешней транзакции на весь LLM-вызов. */
    void generate(UUID requestId) {
        AiRequest request = aiRequests.findById(requestId).orElse(null);
        if (request == null || request.status() != AiRequestStatus.GENERATING) {
            return;
        }
        try {
            aiRequests.renewLease(requestId, Instant.now().plus(properties.getLeaseTtl()));

            LlmProvider.LlmResponse response = llm.complete(new LlmProvider.LlmRequest(
                    request.queryText(), properties.getModel(), properties.getMaxOutputTokens(),
                    List.of()));

            FinalTextBuilder.BuiltText text = textBuilder.build(response.text(), List.of());
            Instant now = Instant.now();

            boolean completed = aiRequests.complete(requestId, text.fullText(), text.sentText(),
                    text.truncated(), null, now);
            if (!completed) {
                log.debug("CAS при завершении проигран requestId={} — сообщение не редактируется", requestId);
                return;
            }
            deliver(request, text.sentText());
        } catch (RuntimeException e) {
            log.warn("Генерация requestId={} завершилась ошибкой {}", requestId, e.getClass().getSimpleName());
            aiRequests.fail(requestId, mapError(e), Instant.now());
        }
    }

    private void deliver(AiRequest request, String sentText) {
        if (request.inlineMessageId() == null || request.inlineMessageId().isBlank()) {
            return;
        }
        InlineMessageEditor.DeliveryResult result = editor.edit(request.inlineMessageId(), sentText);
        if (!result.delivered()) {
            aiRequests.setDeliveryError(request.id(),
                    result == InlineMessageEditor.DeliveryResult.MESSAGE_NOT_FOUND
                            ? ErrorCode.EXPIRED
                            : ErrorCode.DELIVERY_FAILED);
        }
    }

    private static ErrorCode mapError(RuntimeException e) {
        String name = e.getClass().getSimpleName();
        if (name.contains("Timeout")) {
            return ErrorCode.TIMEOUT;
        }
        if (name.contains("Connect") || name.contains("Unavailable")) {
            return ErrorCode.PROVIDER_UNAVAILABLE;
        }
        return ErrorCode.INTERNAL;
    }
}
