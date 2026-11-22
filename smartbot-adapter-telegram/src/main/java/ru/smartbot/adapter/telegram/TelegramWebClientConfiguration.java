package ru.smartbot.adapter.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient для Telegram Bot API (AD-23).
 *
 * <p>Base URL = bot API + /bot&lt;token&gt;; токен не логируется. Таймауты —
 * отдельные для connect/response; транспортные ретраи выполняет вызывающий
 * адаптер (AD-21).
 */
@Configuration
public class TelegramWebClientConfiguration {

    @Bean
    public WebClient telegramWebClient(TelegramProperties properties,
                                       @Value("${smartbot.telegram.bot-token:}") String botToken) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(properties.getPollTimeoutSeconds() + 15L));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl() + "/bot" + botToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
