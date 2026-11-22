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
    private final int maxQueryLength;

    public SmartBotRequestProperties(
            @Value("${smartbot.correlation.ttl:PT10M}") String correlationTtl,
            @Value("${smartbot.callback.ttl:PT1H}") String callbackTtl,
            @Value("${smartbot.limits.max-query-length:256}") int maxQueryLength) {
        this.correlationTtl = Duration.parse(correlationTtl);
        this.callbackTtl = Duration.parse(callbackTtl);
        this.maxQueryLength = maxQueryLength;
    }

    /** Окно переиспользования записи корреляции (AD-13). */
    public Duration getCorrelationTtl() {
        return correlationTtl;
    }

    /** TTL кнопки после публикации: просроченный callback → EXPIRED (AD-15). */
    public Duration getCallbackTtl() {
        return callbackTtl;
    }

    /** Дополнительный cap query до LLM (AD-19). */
    public int getMaxQueryLength() {
        return maxQueryLength;
    }
}
