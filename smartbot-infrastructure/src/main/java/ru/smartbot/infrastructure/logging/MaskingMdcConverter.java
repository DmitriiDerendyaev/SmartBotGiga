package ru.smartbot.infrastructure.logging;

import ch.qos.logback.classic.pattern.MDCConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback-конвертер MDC с маскированием секретов (AD-4): значения MDC
 * маскируются до вывода в любом appender'е — как полный рендер {@code %mdc},
 * так и bare-значения {@code %X{key}} (Bearer/Basic без префикса Authorization,
 * bot-token формы). Подключается через conversionRule с переопределением
 * встроенных слов {@code mdc} и {@code X} в logback.xml/logback-spring.xml.
 */
public class MaskingMdcConverter extends MDCConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SecretMasker.mask(super.convert(event));
    }
}