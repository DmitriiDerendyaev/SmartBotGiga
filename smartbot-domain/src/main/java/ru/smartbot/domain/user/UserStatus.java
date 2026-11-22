package ru.smartbot.domain.user;

/**
 * Статус пользователя (AD-17).
 *
 * <p>Меняется только явным use case в application-impl; upsert регистрации
 * статус НЕ трогает (blocked не разблокируется автоматически). Проверка
 * blocked выполняется до создания AiRequest и до списания квоты.
 *
 * <p>Персистентное представление — lowercase {@code active}/{@code blocked}
 * (ER-форма); маппинг — в adapter-persistence, не в domain.
 */
public enum UserStatus {

    /** Обычный доступ (создаётся при регистрации). */
    ACTIVE,

    /** Доступ к генерации запрещён; отказ {@code ErrorCode.BLOCKED}. */
    BLOCKED
}
