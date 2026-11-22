package ru.smartbot.adapter.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.smartbot.adapter.telegram.model.TelegramInlineResult;
import ru.smartbot.adapter.telegram.model.TelegramUpdate;
import ru.smartbot.application.api.request.HandleInlineQueryUseCase;
import ru.smartbot.domain.error.DomainException;
import ru.smartbot.domain.error.ErrorCode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InlineQueryHandler — inline_query → лёгкий результат (AD-8/13/19/20)")
class InlineQueryHandlerTest {

    private final HandleInlineQueryUseCase useCase = mock(HandleInlineQueryUseCase.class);
    private final TelegramApiClient api = mock(TelegramApiClient.class);
    private final InlineQueryHandler handler = new InlineQueryHandler(useCase, api);

    @Test
    @DisplayName("валидный query: article с кнопкой и opaque token в callback_data")
    void buildsArticleWithGenerateButton() {
        UUID requestId = UUID.randomUUID();
        when(useCase.handle(any())).thenReturn(
                new HandleInlineQueryUseCase.Result(requestId, "g:token123", false, false));

        handler.handle(update("Как работает Kafka?", 10L, "<user>"));

        ArgumentCaptor<List<TelegramInlineResult>> captor = listCaptor();
        verify(api).answerInlineQuery(eq("iq-1"), captor.capture());
        TelegramInlineResult result = captor.getValue().get(0);

        assertThat(result.type()).isEqualTo("article");
        assertThat(result.replyMarkup().inlineKeyboard().get(0).get(0).callbackData())
                .isEqualTo("g:token123");
        assertThat(result.replyMarkup().inlineKeyboard().get(0).get(0).text())
                .isEqualTo("Сгенерировать ответ");
        assertThat(result.inputMessageContent().parseMode()).isEqualTo("HTML");
        assertThat(result.inputMessageContent().messageText()).contains("Как работает Kafka?");
    }

    @Test
    @DisplayName("HTML в query экранируется (parse_mode=HTML)")
    void escapesHtml() {
        when(useCase.handle(any())).thenReturn(
                new HandleInlineQueryUseCase.Result(UUID.randomUUID(), "g:t", false, false));

        handler.handle(update("<script>alert(1)</script>", 11L, "u"));

        ArgumentCaptor<List<TelegramInlineResult>> captor = listCaptor();
        verify(api).answerInlineQuery(eq("iq-1"), captor.capture());
        assertThat(captor.getValue().get(0).inputMessageContent().messageText())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("дубль update: ответ не отправляется")
    void duplicateSendsNothing() {
        when(useCase.handle(any())).thenReturn(HandleInlineQueryUseCase.Result.alreadyProcessed());

        handler.handle(update("q", 12L, "u"));

        verify(api, never()).answerInlineQuery(any(), anyList());
    }

    @Test
    @DisplayName("доменный отказ: безопасный текст из каталога, без деталей")
    void domainErrorUsesSafeCatalog() {
        when(useCase.handle(any())).thenThrow(new DomainException(ErrorCode.MESSAGE_TOO_LONG));

        handler.handle(update("q", 13L, "u"));

        ArgumentCaptor<List<TelegramInlineResult>> captor = listCaptor();
        verify(api).answerInlineQuery(eq("iq-1"), captor.capture());
        String text = captor.getValue().get(0).inputMessageContent().messageText();
        assertThat(text).doesNotContain("MESSAGE_TOO_LONG").isNotBlank();
        assertThat(captor.getValue().get(0).replyMarkup()).isNull();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<TelegramInlineResult>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private static TelegramUpdate update(String query, long updateId, String username) {
        return new TelegramUpdate(updateId,
                new TelegramUpdate.InlineQuery("iq-1",
                        new TelegramUpdate.User(42L, username, "U", "ru"), query, ""),
                null);
    }
}
