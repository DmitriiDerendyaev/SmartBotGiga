package ru.smartbot.application.impl.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.smartbot.application.api.user.EnsureUserUseCase;
import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.application.api.user.UserRepository;
import ru.smartbot.application.api.user.UserView;
import ru.smartbot.domain.user.User;

import java.util.Objects;

/**
 * Авторегистрация пользователя (AD-17, FR15): единственный владелец User.
 *
 * <p>Атомарный get-or-create делегируется порту {@link UserRepository}
 * (реализация — native upsert в adapter-persistence); сервис не делает
 * read-then-write и не мутирует status/settings. Повторный update обновляет
 * только profile-поля и last_seen_at; blocked не разблокируется.
 *
 * <p>Транзакция — REQUIRED: регистрация входит в одну транзакцию с проверкой
 * лимита вызывающего use case (AD-17/AD-18, story 1.3). Признак blocked
 * возвращается в результате; отказ BLOCKED до создания AiRequest выполняет
 * вызывающий use case (story 1.3).
 */
@Service
public class EnsureUserService implements EnsureUserUseCase {

    private final UserRepository userRepository;

    public EnsureUserService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Override
    @Transactional
    public Result ensureUser(TelegramPrincipal principal) {
        Objects.requireNonNull(principal, "principal");

        UserRepository.Registration registration = userRepository.getOrCreate(principal);
        User user = registration.user();
        return new Result(toView(user), registration.created(), user.isBlocked());
    }

    private static UserView toView(User user) {
        return new UserView(
                user.id(),
                user.telegramUserId(),
                user.username(),
                user.firstName(),
                user.languageCode(),
                user.status(),
                user.createdAt(),
                user.lastSeenAt());
    }
}
