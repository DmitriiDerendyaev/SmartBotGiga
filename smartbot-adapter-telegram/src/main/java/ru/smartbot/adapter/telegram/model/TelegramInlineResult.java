package ru.smartbot.adapter.telegram.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Минимальные модели ответа Bot API (inline mode, AD-8/19/23).
 *
 * <p>Кнопка Generate Answer — только на опубликованном сообщении
 * (input_message_content + reply_markup); callback_data = opaque token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelegramInlineResult(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("input_message_content") InputMessageContent inputMessageContent,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public static TelegramInlineResult article(String id, String title, String description,
                                               String messageText, String buttonText,
                                               String callbackData) {
        ReplyMarkup markup = (buttonText == null || callbackData == null)
                ? null
                : new ReplyMarkup(List.of(List.of(new InlineButton(buttonText, callbackData))));
        return new TelegramInlineResult(
                "article", id, title, description,
                new InputMessageContent(messageText, "HTML"),
                markup);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InputMessageContent(
            @JsonProperty("message_text") String messageText,
            @JsonProperty("parse_mode") String parseMode) {
    }

    public record ReplyMarkup(@JsonProperty("inline_keyboard") List<List<InlineButton>> inlineKeyboard) {
    }

    public record InlineButton(
            @JsonProperty("text") String text,
            @JsonProperty("callback_data") String callbackData) {
    }
}
