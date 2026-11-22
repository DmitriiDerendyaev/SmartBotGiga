package ru.smartbot.adapter.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.smartbot.adapter.telegram.model.TelegramUpdate;
import ru.smartbot.application.api.request.GenerateAnswerUseCase;
import ru.smartbot.application.api.request.HandleCallbackUseCase;
import ru.smartbot.domain.error.DomainException;
import ru.smartbot.domain.error.ErrorCode;
import ru.smartbot.domain.request.AiRequestStatus;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CallbackQueryHandler — немедленный ack и безопасные ошибки (AD-8/20/23)")
class CallbackQueryHandlerTest {

    private final HandleCallbackUseCase useCase = mock(HandleCallbackUseCase.class);
    private final GenerateAnswerUseCase generateAnswer = mock(GenerateAnswerUseCase.class);
    private final TelegramApiClient api = mock(TelegramApiClient.class);
    private final CallbackQueryHandler handler = new CallbackQueryHandler(useCase, generateAnswer, api);

    @Test
    @DisplayName("валидный callback: answerCallbackQuery отправлен")
    void validCallbackAcked() {
        when(useCase.handle(any())).thenReturn(
                new HandleCallbackUseCase.Result(UUID.randomUUID(), AiRequestStatus.CREATED, false, true));

        handler.handle(update("g:token", "im-1"));

        verify(api).answerCallbackQuery("cb-1", null);
        verify(generateAnswer).start(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("дубль: повторный ack не отправляется")
    void duplicateNotAcked() {
        when(useCase.handle(any())).thenReturn(
                new HandleCallbackUseCase.Result(null, null, true, false));

        handler.handle(update("g:token", "im-1"));

        verify(api, never()).answerCallbackQuery(any(), any());
    }

    @Test
    @DisplayName("доменный отказ: alert с безопасным текстом, без кода")
    void domainErrorUsesSafeText() {
        when(useCase.handle(any())).thenThrow(new DomainException(ErrorCode.EXPIRED));

        handler.handle(update("g:token", "im-1"));

        verify(api).answerCallbackQuery(org.mockito.ArgumentMatchers.eq("cb-1"),
                org.mockito.ArgumentMatchers.argThat(text ->
                        text != null && !text.contains("EXPIRED") && !text.isBlank()));
    }

    private static TelegramUpdate update(String data, String inlineMessageId) {
        return new TelegramUpdate(1L, null,
                new TelegramUpdate.CallbackQuery("cb-1",
                        new TelegramUpdate.User(42L, "u", "U", "ru"), inlineMessageId, data));
    }
}
