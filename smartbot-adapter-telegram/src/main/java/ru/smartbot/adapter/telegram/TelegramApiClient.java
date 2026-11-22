package ru.smartbot.adapter.telegram;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.smartbot.adapter.telegram.model.TelegramInlineResult;
import ru.smartbot.adapter.telegram.model.TelegramUpdate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Клиент Telegram Bot API (AD-23). Весь исходящий трафик — через настроенный
 * baseUrl (в prod — VPN-маршрут, Epic 5).
 *
 * <p>Токен — часть URL Bot API: клиент не логирует URL и тела (redaction в
 * infrastructure дополнительно маскирует паттерн bot-токена).
 */
@Component
public class TelegramApiClient {

    private final WebClient client;
    private final int pollTimeoutSeconds;

    public TelegramApiClient(WebClient telegramWebClient, TelegramProperties properties) {
        this.client = telegramWebClient;
        this.pollTimeoutSeconds = properties.getPollTimeoutSeconds();
    }

    /**
     * Long polling. Offset = update_id + 1 (at-least-once; подтверждение — только
     * после персистентной обработки, см. TelegramPollingService).
     */
    public List<TelegramUpdate> getUpdates(long offset) {
        GetUpdatesResponse response = client.post()
                .uri(uri -> uri.path("/getUpdates")
                        .queryParam("offset", offset)
                        .queryParam("timeout", pollTimeoutSeconds)
                        .queryParam("allowed_updates", "[\"inline_query\",\"callback_query\",\"chosen_inline_result\"]")
                        .build())
                .retrieve()
                .bodyToMono(GetUpdatesResponse.class)
                .block(Duration.ofSeconds(pollTimeoutSeconds + 10L));
        return response == null || response.result() == null ? List.of() : response.result();
    }

    /** Отправить inline-результаты (is_personal=true, cache_time=0 — AD-8). */
    public void answerInlineQuery(String inlineQueryId, List<TelegramInlineResult> results) {
        client.post()
                .uri("/answerInlineQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "inline_query_id", inlineQueryId,
                        "results", results,
                        "is_personal", true,
                        "cache_time", 0))
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(15));
    }

    /** Немедленный ack callback (AD-8/23). */
    public void answerCallbackQuery(String callbackQueryId, String text) {
        client.post()
                .uri("/answerCallbackQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(text == null
                        ? Map.of("callback_query_id", callbackQueryId)
                        : Map.of("callback_query_id", callbackQueryId, "text", text, "show_alert", false))
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(15));
    }

    /** Ответ getUpdates (Telegram envelope). */
    record GetUpdatesResponse(boolean ok, List<TelegramUpdate> result) {
    }
}
