package ru.smartbot.domain.error;

import java.util.Map;
import java.util.Objects;

/**
 * Доменное исключение (AD-20): несёт только {@link ErrorCode} и типизированные
 * безопасные параметры.
 *
 * <p>Сырые внешние тела/заголовки не пересекают порт: конструкторы намеренно
 * не принимают ни raw message, ни cause, ни произвольные объекты — только
 * строковые параметры, которые по контракту не содержат секретов и PII
 * (идентификаторы, счётчики, имена кодов). Адаптер логирует сырые детали сам,
 * с redaction (AD-4), и пробрасывает наружу только этот тип.
 *
 * <p>User-facing текст рендерится единственной таблицей
 * {@code SafeErrorMessages} в application-api; {@link #getMessage()} —
 * служебный (имя кода) и никогда не используется для пользователя.
 * Параметры предназначены ТОЛЬКО для логов/диагностики и пользователю
 * не рендерятся.
 */
public final class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final Map<String, String> parameters;

    public DomainException(ErrorCode code) {
        this(code, Map.of());
    }

    /**
     * @param code           доменный код ошибки, не {@code null}
     * @param safeParameters типизированные безопасные параметры (например,
     *                       {@code telegramUserId}, {@code limit}); не {@code null},
     *                       без null-ключей/значений. Сырые payload запрещены.
     *                       Только для логов/диагностики; пользователю не рендерятся.
     */
    public DomainException(ErrorCode code, Map<String, String> safeParameters) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
        this.parameters = Map.copyOf(Objects.requireNonNull(safeParameters, "safeParameters"));
    }

    public ErrorCode getCode() {
        return code;
    }

    /** Неизменяемая копия безопасных параметров. */
    public Map<String, String> getParameters() {
        return parameters;
    }
}