package ru.smartbot.application.api.user;

import java.util.Objects;

/**
 * Входящий контракт авторегистрации (AD-17, FR15): вызывается на первом
 * любом update, идемпотентно. Реализация — application-impl (единственный
 * владелец User); адаптер передаёт {@link TelegramPrincipal} и не регистрирует
 * пользователя сам.
 *
 * <p>Blocked-признак возвращается в результате: upsert не разблокирует.
 * Отказ {@code BLOCKED} (DomainException с ErrorCode из story 1.1) выполняет
 * вызывающий use case ДО создания AiRequest — контракт фиксируется сейчас,
 * вызов — в story 1.3 (AD-17/AD-18).
 */
public interface EnsureUserUseCase {

    /**
     * Атомарный get-or-create User + UserSettings (history_enabled=false).
     *
     * @param principal идентификация из Telegram, не {@code null}
     * @return UserView + признак регистрации этим вызовом + признак blocked
     */
    Result ensureUser(TelegramPrincipal principal);

    /**
     * Результат авторегистрации.
     *
     * @param user       актуальное представление User
     * @param registered true, если User создан этим вызовом (первый update)
     * @param blocked    true, если status=blocked; вызывающий use case обязан
     *                   отказать (ErrorCode.BLOCKED) до создания AiRequest
     */
    record Result(UserView user, boolean registered, boolean blocked) {

        public Result {
            Objects.requireNonNull(user, "user");
        }
    }
}
