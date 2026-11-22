package ru.smartbot.application.api.error;

import ru.smartbot.domain.error.ErrorCode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Единственная таблица {@link ErrorCode} → безопасный user-facing текст (AD-20).
 *
 * <p>Чистая Java, без Spring. Адаптеры не содержат собственного маппинга и
 * никогда не рендерят {@code getMessage()} — только текст из этой таблицы.
 * Тексты на русском, без внутренних деталей (провайдеры, коды внешних API,
 * stacktrace). Отсутствующая запись — fail-closed {@link IllegalStateException},
 * а не fallthrough-рендеринг сырого сообщения.
 */
public final class SafeErrorMessages {

    private static final Map<ErrorCode, String> MESSAGES;

    static {
        Map<ErrorCode, String> messages = new EnumMap<>(ErrorCode.class);
        messages.put(ErrorCode.VALIDATION, "Некорректный запрос. Проверьте данные и попробуйте ещё раз.");
        messages.put(ErrorCode.INVALID_CALLBACK, "Кнопка недействительна. Отправьте новый запрос.");
        messages.put(ErrorCode.EXPIRED, "Время действия запроса истекло. Отправьте новый запрос.");
        messages.put(ErrorCode.BLOCKED, "Доступ к боту для вас ограничен.");
        messages.put(ErrorCode.RATE_LIMITED, "Слишком много запросов. Пожалуйста, попробуйте позже.");
        messages.put(ErrorCode.QUOTA_EXCEEDED, "Лимит запросов исчерпан. Попробуйте позже.");
        messages.put(ErrorCode.BUDGET_EXCEEDED, "Достигнут лимит использования. Попробуйте позже.");
        messages.put(ErrorCode.TIMEOUT, "Сервис не ответил вовремя. Попробуйте ещё раз.");
        messages.put(ErrorCode.PROVIDER_UNAVAILABLE, "Сервис временно недоступен. Попробуйте позже.");
        messages.put(ErrorCode.AUTH, "Не удалось выполнить авторизацию. Попробуйте позже.");
        messages.put(ErrorCode.SEARCH_UNAVAILABLE, "Поиск временно недоступен. Попробуйте позже.");
        messages.put(ErrorCode.DELIVERY_FAILED, "Не удалось доставить сообщение. Попробуйте ещё раз.");
        messages.put(ErrorCode.MESSAGE_TOO_LONG, "Запрос слишком длинный. Сократите его и попробуйте ещё раз.");
        messages.put(ErrorCode.RESTARTED, "Обработка была прервана. Отправьте запрос ещё раз.");
        messages.put(ErrorCode.INTERNAL, "Произошла внутренняя ошибка. Попробуйте позже.");
        MESSAGES = Collections.unmodifiableMap(messages);
    }

    private SafeErrorMessages() {
    }

    /**
     * Безопасный текст для кода.
     *
     * @throws NullPointerException  если код {@code null}
     * @throws IllegalStateException если для кода нет записи (fail-closed)
     */
    public static String messageFor(ErrorCode code) {
        Objects.requireNonNull(code, "code");
        String text = MESSAGES.get(code);
        if (text == null) {
            throw new IllegalStateException("Нет безопасного текста для кода: " + code);
        }
        return text;
    }
}