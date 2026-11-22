package ru.smartbot.adapter.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Конфигурация Telegram Bot API (AD-5, AD-23).
 *
 * <p>Токен — только из env/Infisical (никогда в коде/логах). Base URL
 * конфигурируем: в тестах — WireMock, в prod — https://api.telegram.org.
 * Polling выключен по умолчанию и включается после проверки VPN-маршрута
 * (Epic 5).
 */
@Component
@ConfigurationProperties(prefix = "smartbot.telegram")
public class TelegramProperties {

    /** Включить long polling (после Phase 0/Epic 5). */
    private boolean pollingEnabled = false;

    /** Базовый URL Bot API (в тестах — WireMock). */
    private String baseUrl = "https://api.telegram.org";

    /** Bot token: только env/Infisical, в логи не попадает (AD-4). */
    private String botToken = "";

    /** Таймаут long polling, сек (Telegram рекомендует 25–50). */
    private int pollTimeoutSeconds = 25;

    /** Backoff после ошибки polling. */
    private Duration pollErrorBackoff = Duration.ofSeconds(3);

    public boolean isPollingEnabled() {
        return pollingEnabled;
    }

    public void setPollingEnabled(boolean pollingEnabled) {
        this.pollingEnabled = pollingEnabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public int getPollTimeoutSeconds() {
        return pollTimeoutSeconds;
    }

    public void setPollTimeoutSeconds(int pollTimeoutSeconds) {
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }

    public Duration getPollErrorBackoff() {
        return pollErrorBackoff;
    }

    public void setPollErrorBackoff(Duration pollErrorBackoff) {
        this.pollErrorBackoff = pollErrorBackoff;
    }
}
