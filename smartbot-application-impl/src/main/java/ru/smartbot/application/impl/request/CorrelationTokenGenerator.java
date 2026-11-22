package ru.smartbot.application.impl.request;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Генерация opaque correlation token (AD-13).
 *
 * <p>Формат: {@code g:} + 32 hex UUID без дефисов = 34 символа (&le;64 байт
 * callback_data). Формат, генерация и валидация живут только в impl; адаптер
 * передаёт token непрозрачно и не парсит его.
 */
@Component
public class CorrelationTokenGenerator {

    public String generate() {
        return "g:" + UUID.randomUUID().toString().replace("-", "");
    }
}
