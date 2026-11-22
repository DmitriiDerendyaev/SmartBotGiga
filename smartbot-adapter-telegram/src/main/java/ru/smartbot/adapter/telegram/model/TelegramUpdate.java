package ru.smartbot.adapter.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Минимальная модель Telegram Update (inline mode).
 * Неизвестные поля игнорируются; контекст диалога не используется (FR3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        @JsonProperty("inline_query") InlineQuery inlineQuery,
        @JsonProperty("callback_query") CallbackQuery callbackQuery) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InlineQuery(
            @JsonProperty("id") String id,
            @JsonProperty("from") User from,
            @JsonProperty("query") String query,
            @JsonProperty("offset") String offset) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(
            @JsonProperty("id") String id,
            @JsonProperty("from") User from,
            @JsonProperty("inline_message_id") String inlineMessageId,
            @JsonProperty("data") String data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(
            @JsonProperty("id") long id,
            @JsonProperty("username") String username,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("language_code") String languageCode) {
    }
}
