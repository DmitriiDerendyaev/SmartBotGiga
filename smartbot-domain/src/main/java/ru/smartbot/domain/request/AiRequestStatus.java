package ru.smartbot.domain.request;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Статус AiRequest и граф допустимых переходов (AD-15, AD-3).
 *
 * <p>Граф enforced здесь, в domain, а не в SQL:
 * <pre>
 * CREATED    → GENERATING | EXPIRED | FAILED
 * GENERATING → COMPLETED  | FAILED  | EXPIRED
 * COMPLETED / FAILED / EXPIRED — терминальные, immutable
 * </pre>
 *
 * <p>Переход в persistence — атомарный CAS по ожидаемому статусу
 * ({@code UPDATE … WHERE status = :expected}); проигравший CAS не редактирует
 * сообщение (AD-15).
 */
public enum AiRequestStatus {

    CREATED,
    GENERATING,
    COMPLETED,
    FAILED,
    EXPIRED;

    private static final Map<AiRequestStatus, Set<AiRequestStatus>> TRANSITIONS = Map.of(
            CREATED, EnumSet.of(GENERATING, EXPIRED, FAILED),
            GENERATING, EnumSet.of(COMPLETED, FAILED, EXPIRED),
            COMPLETED, EnumSet.noneOf(AiRequestStatus.class),
            FAILED, EnumSet.noneOf(AiRequestStatus.class),
            EXPIRED, EnumSet.noneOf(AiRequestStatus.class));

    /** Допустим ли переход в {@code target} из текущего статуса. */
    public boolean canTransitionTo(AiRequestStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Терминальный статус: дальнейшие переходы запрещены (immutable). */
    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** Активный статус: участвует в бизнес-unique «один активный на публикацию». */
    public boolean isActive() {
        return this == CREATED || this == GENERATING;
    }
}
