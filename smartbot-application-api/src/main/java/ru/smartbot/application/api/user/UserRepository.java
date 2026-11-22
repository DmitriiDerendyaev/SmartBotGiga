package ru.smartbot.application.api.user;

import ru.smartbot.domain.user.User;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт репозитория User (outbound, AD-17). Реализация — adapter-persistence.
 *
 * <p>Единственный владелец User — application-impl: адаптеры не регистрируют
 * пользователя и не мутируют домен, а передают {@link TelegramPrincipal}.
 * Реализация обязана обеспечить атомарный get-or-create
 * ({@code INSERT … ON CONFLICT (telegram_user_id) DO UPDATE} только
 * profile-полей и {@code last_seen_at}); {@code status}/{@code history_enabled}
 * upsert НЕ трогает.
 */
public interface UserRepository {

    /**
     * Атомарный get-or-create по {@code telegram_user_id}.
     *
     * <p>Первый вызов создаёт User (active) и UserSettings
     * ({@code history_enabled=false}); повторный — обновляет только
     * profile-поля и {@code last_seen_at}. Параллельные первые вызовы
     * одного telegram_user_id дают ровно один User и один id.
     *
     * @param principal идентификация из Telegram, не {@code null}
     * @return актуальное состояние User и признак создания этим вызовом
     */
    Registration getOrCreate(TelegramPrincipal principal);

    /** Поиск по внутреннему UUID. */
    Optional<User> findById(UUID id);

    /** Поиск по Telegram id (BIGINT). */
    Optional<User> findByTelegramUserId(long telegramUserId);

    /**
     * Результат get-or-create.
     *
     * @param user    актуальное состояние (включая status — upsert его не меняет)
     * @param created true, если User создан этим вызовом (первый update)
     */
    record Registration(User user, boolean created) {

        public Registration {
            Objects.requireNonNull(user, "user");
        }
    }
}
