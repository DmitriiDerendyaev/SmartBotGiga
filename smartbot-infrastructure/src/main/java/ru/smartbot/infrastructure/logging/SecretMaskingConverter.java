package ru.smartbot.infrastructure.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback-конвертер {@code %maskedMsg}: маскирует секреты в форматированном
 * сообщении до вывода в appender (AD-4). Подключается через conversionRule
 * в logback-spring.xml; сырой {@code %msg} в appender'ах запрещён.
 */
public class SecretMaskingConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SecretMasker.mask(super.convert(event));
    }
}