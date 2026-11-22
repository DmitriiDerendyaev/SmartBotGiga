package ru.smartbot.application.impl.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.smartbot.application.api.llm.LlmProvider;
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.application.api.telegram.InlineMessageEditor;
import ru.smartbot.domain.error.ErrorCode;
import ru.smartbot.domain.request.AiRequest;
import ru.smartbot.domain.request.AiRequestStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GenerateAnswerService — CAS, lease, доставка (AD-15/19)")
class GenerateAnswerServiceTest {

    private final AiRequestRepository repository = mock(AiRequestRepository.class);
    private final LlmProvider llm = mock(LlmProvider.class);
    private final InlineMessageEditor editor = mock(InlineMessageEditor.class);
    private final Executor direct = Runnable::run;
    private final SmartBotRequestProperties properties =
            new SmartBotRequestProperties("PT10M", "PT1H", "PT2M", 256, 1024, "mock");

    private final GenerateAnswerService service =
            new GenerateAnswerService(repository, llm, editor, new FinalTextBuilder(), properties, direct);

    private final UUID requestId = UUID.randomUUID();

    @Test
    @DisplayName("CAS выигран: GENERATING → COMPLETED → editMessageText")
    void happyPath() {
        when(repository.transition(eq(requestId), eq(AiRequestStatus.CREATED),
                eq(AiRequestStatus.GENERATING), any())).thenReturn(true);
        when(repository.findById(requestId)).thenReturn(Optional.of(active("im-1")));
        when(llm.complete(any())).thenReturn(new LlmProvider.LlmResponse(
                "ответ", "mock", new LlmProvider.LlmUsage(10, 5, 15), 20));
        when(repository.complete(eq(requestId), anyString(), anyString(), anyBoolean(), eq(null), any()))
                .thenReturn(true);
        when(editor.edit(anyString(), anyString())).thenReturn(InlineMessageEditor.DeliveryResult.DELIVERED);

        service.start(requestId);

        verify(repository, org.mockito.Mockito.atLeastOnce()).renewLease(eq(requestId), any());
        verify(repository).complete(eq(requestId), anyString(), anyString(), anyBoolean(), eq(null), any());
        verify(editor).edit(eq("im-1"), anyString());
    }

    @Test
    @DisplayName("CAS при завершении проигран: сообщение не редактируется")
    void lostCasDoesNotEdit() {
        when(repository.transition(eq(requestId), eq(AiRequestStatus.CREATED),
                eq(AiRequestStatus.GENERATING), any())).thenReturn(true);
        when(repository.findById(requestId)).thenReturn(Optional.of(active("im-1")));
        when(llm.complete(any())).thenReturn(new LlmProvider.LlmResponse(
                "ответ", "mock", new LlmProvider.LlmUsage(1, 1, 2), 1));
        when(repository.complete(any(), anyString(), anyString(), anyBoolean(), eq(null), any()))
                .thenReturn(false);

        service.start(requestId);

        verify(editor, never()).edit(anyString(), anyString());
    }

    @Test
    @DisplayName("ошибка доставки фиксируется отдельно от результата генерации")
    void deliveryErrorRecordedSeparately() {
        when(repository.transition(eq(requestId), eq(AiRequestStatus.CREATED),
                eq(AiRequestStatus.GENERATING), any())).thenReturn(true);
        when(repository.findById(requestId)).thenReturn(Optional.of(active("im-1")));
        when(llm.complete(any())).thenReturn(new LlmProvider.LlmResponse(
                "ответ", "mock", new LlmProvider.LlmUsage(1, 1, 2), 1));
        when(repository.complete(any(), anyString(), anyString(), anyBoolean(), eq(null), any()))
                .thenReturn(true);
        when(editor.edit(anyString(), anyString()))
                .thenReturn(InlineMessageEditor.DeliveryResult.MESSAGE_NOT_FOUND);

        service.start(requestId);

        verify(repository).setDeliveryError(requestId, ErrorCode.EXPIRED);
    }

    @Test
    @DisplayName("ошибка LLM: запрос → FAILED с доменным кодом")
    void llmFailureMarksFailed() {
        when(repository.transition(eq(requestId), eq(AiRequestStatus.CREATED),
                eq(AiRequestStatus.GENERATING), any())).thenReturn(true);
        when(repository.findById(requestId)).thenReturn(Optional.of(active("im-1")));
        when(llm.complete(any())).thenThrow(new IllegalStateException("boom"));

        service.start(requestId);

        verify(repository).fail(eq(requestId), eq(ErrorCode.INTERNAL), any());
        verify(editor, never()).edit(anyString(), anyString());
    }

    @Test
    @DisplayName("повторный start по GENERATING не запускает второй исполнитель")
    void startIsIdempotent() {
        when(repository.transition(eq(requestId), eq(AiRequestStatus.CREATED),
                eq(AiRequestStatus.GENERATING), any())).thenReturn(false);
        when(repository.findById(requestId)).thenReturn(Optional.of(active("im-1")));

        service.start(requestId);

        verify(llm, never()).complete(any());
    }

    private AiRequest active(String inlineMessageId) {
        return new AiRequest(requestId, UUID.randomUUID(), null, 1L, inlineMessageId,
                "g:token", "вопрос", AiRequestStatus.GENERATING, null, null, false,
                null, null, Instant.now().plusSeconds(60), Instant.now(), null);
    }
}
