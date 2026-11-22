package ru.smartbot.infrastructure.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * Logback-конвертер {@code %maskedEx}: маскирует секреты во всём рендере
 * stacktrace — сообщения исключений, cause-цепочка, suppressed (AD-4).
 * Подключается через conversionRule в logback-spring.xml; сырые
 * {@code %ex}/{@code %throwable} в appender'ах запрещены.
 */
public class MaskingThrowableProxyConverter extends ThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy throwableProxy) {
        return SecretMasker.mask(super.throwableProxyToString(throwableProxy));
    }
}