package ru.smartbot.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.smartbot.application.api.telegram.InlineMessageEditor;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Реализация порта {@link InlineMessageEditor} через editMessageText (AD-8/23).
 *
 * <p>Транспортные повторы (429) — внутри адаптера (AD-21). «message is not
 * modified» = успех; «message to edit not found» = сообщение истекло/удалено.
 * Текст приходит готовым от impl и не переформатируется (AD-19).
 */
@Component
public class TelegramInlineMessageEditor implements InlineMessageEditor {

    private static final Logger log = LoggerFactory.getLogger(TelegramInlineMessageEditor.class);

    private final WebClient client;

    public TelegramInlineMessageEditor(WebClient telegramWebClient) {
        this.client = Objects.requireNonNull(telegramWebClient, "telegramWebClient");
    }

    @Override
    public DeliveryResult edit(String inlineMessageId, String finalText) {
        try {
            ApiResponse response = client.post()
                    .uri("/editMessageText")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "inline_message_id", inlineMessageId,
                            "text", finalText,
                            "parse_mode", "HTML"))
                    .retrieve()
                    .onStatus(status -> status.value() == 400, r -> r.bodyToMono(String.class)
                            .map(TelegramApiException::new))
                    .bodyToMono(ApiResponse.class)
                    .block(Duration.ofSeconds(15));
            return response != null && response.ok() ? DeliveryResult.DELIVERED : DeliveryResult.FAILED;
        } catch (TelegramApiException e) {
            return classify(e.description);
        } catch (RuntimeException e) {
            log.warn("editMessageText не удался: {}", e.getClass().getSimpleName());
            return DeliveryResult.FAILED;
        }
    }

    static DeliveryResult classify(String description) {
        String value = description == null ? "" : description.toLowerCase();
        if (value.contains("not modified")) {
            return DeliveryResult.NOT_MODIFIED;
        }
        if (value.contains("message to edit not found") || value.contains("message not found")) {
            return DeliveryResult.MESSAGE_NOT_FOUND;
        }
        return DeliveryResult.FAILED;
    }

    record ApiResponse(boolean ok) {
    }

    static final class TelegramApiException extends RuntimeException {
        private final String description;

        TelegramApiException(String description) {
            super("Telegram API error"); // сырое тело не пересекает порт (AD-20)
            this.description = description;
        }
    }
}
