package ru.smartbot.application.impl.request;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Конфигурация корреляции и лимитов ввода (AD-10, AD-13, AD-19).
 *
 * <p>Использует {@code @Value} (spring-context), а не
 * {@code @ConfigurationProperties}: Boot-зависимости в application-impl
 * запрещены матрицей AD-2. Все значения конфигурируемы, дефолты консервативные.
 */
@Component
public class SmartBotRequestProperties {

    private final Duration correlationTtl;
    private final Duration callbackTtl;
    private final Duration leaseTtl;
    private final int maxQueryLength;
    private final int maxOutputTokens;
    private final String model;

    public SmartBotRequestProperties(
            @Value("${smartbot.correlation.ttl:PT10M}") String correlationTtl,
            @Value("${smartbot.callback.ttl:PT1H}") String callbackTtl,
            @Value("${smartbot.generation.lease:PT2M}") String leaseTtl,
            @Value("${smartbot.limits.max-query-length:256}") int maxQueryLength,
            @Value("${smartbot.limits.max-output-tokens:1024}") int maxOutputTokens,
            @Value("${smartbot.llm.model:mock}") String model) {
        this.correlationTtl = Duration.parse(correlationTtl);
        this.callbackTtl = Duration.parse(callbackTtl);
        this.leaseTtl = Duration.parse(leaseTtl);
        this.maxQueryLength = maxQueryLength;
        this.maxOutputTokens = maxOutputTokens;
        this.model = model;
    }

    /** Окно переиспользования записи корреляции (AD-13). */
    public Duration getCorrelationTtl() {
        return correlationTtl;
    }

    /** TTL кнопки после публикации: просроченный callback → EXPIRED (AD-15). */
    public Duration getCallbackTtl() {
        return callbackTtl;
    }

    /** Lease исполнителя: heartbeat; истёкший lease → FAILED RESTARTED (AD-15). */
    public Duration getLeaseTtl() {
        return leaseTtl;
    }

    /** Единственный источник лимита выхода (AD-18). */
    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    /** Эффективная модель (AD-24). */
    public String getModel() {
        return model;
    }

    /** Дополнительный cap query до LLM (AD-19). */
    public int getMaxQueryLength() {
        return maxQueryLength;
    }
}
