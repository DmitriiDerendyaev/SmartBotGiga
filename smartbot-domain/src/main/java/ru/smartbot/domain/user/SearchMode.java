package ru.smartbot.domain.user;

/**
 * Режим поиска пользователя (AD-7, ER-форма user_settings.search_mode).
 *
 * <p>Значения зафиксированы ER: NONE/NATIVE/EXTERNAL/AUTO. Дефолт при
 * регистрации — из global-настройки {@code smartbot.search.mode} (NONE).
 * Использование режимов — Phase 5; форма фиксируется сейчас (AD-9).
 */
public enum SearchMode {

    /** Поиск не используется. */
    NONE,

    /** Встроенный поиск LLM-провайдера. */
    NATIVE,

    /** Внешний поисковый провайдер. */
    EXTERNAL,

    /** Выбор режима на стороне ядра. */
    AUTO
}
