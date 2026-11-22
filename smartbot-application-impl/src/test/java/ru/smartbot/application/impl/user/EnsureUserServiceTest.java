package ru.smartbot.application.impl.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.application.api.user.UserRepository;
import ru.smartbot.domain.user.User;
import ru.smartbot.domain.user.UserStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EnsureUserService — авторегистрация (AD-17)")
class EnsureUserServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final EnsureUserService service = new EnsureUserService(repository);

    private static final TelegramPrincipal PRINCIPAL =
            new TelegramPrincipal(42L, "dmitrii", "Dmitrii", "ru");

    @Test
    @DisplayName("первый update: создаёт пользователя, registered=true, не blocked")
    void firstUpdateCreatesUser() {
        UUID id = UUID.randomUUID();
        when(repository.getOrCreate(any())).thenReturn(
                new UserRepository.Registration(user(id, UserStatus.ACTIVE), true));

        EnsureUserUseCaseResult result = call();

        assertThat(result.registered()).isTrue();
        assertThat(result.blocked()).isFalse();
        assertThat(result.user().id()).isEqualTo(id);
        assertThat(result.user().telegramUserId()).isEqualTo(42L);
        verify(repository).getOrCreate(PRINCIPAL);
    }

    @Test
    @DisplayName("повторный update: registered=false, профиль обновлён")
    void repeatUpdateNotRegisteredAgain() {
        when(repository.getOrCreate(any())).thenReturn(
                new UserRepository.Registration(user(UUID.randomUUID(), UserStatus.ACTIVE), false));

        assertThat(call().registered()).isFalse();
    }

    @Test
    @DisplayName("blocked не разблокируется: результат несёт blocked=true")
    void blockedUserStaysBlocked() {
        when(repository.getOrCreate(any())).thenReturn(
                new UserRepository.Registration(user(UUID.randomUUID(), UserStatus.BLOCKED), false));

        EnsureUserUseCaseResult result = call();

        assertThat(result.blocked()).isTrue();
        assertThat(result.user().status()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    @DisplayName("минимизация: principal без profile-полей валиден")
    void minimalPrincipalIsValid() {
        TelegramPrincipal minimal = new TelegramPrincipal(7L, null, null, null);
        when(repository.getOrCreate(minimal)).thenReturn(
                new UserRepository.Registration(
                        new User(UUID.randomUUID(), 7L, null, null, null,
                                Instant.now(), Instant.now(), UserStatus.ACTIVE),
                        true));

        assertThat(service.ensureUser(minimal).user().username()).isNull();
    }

    @Test
    @DisplayName("findById/findByTelegramUserId делегируются репозиторию")
    void lookupDelegates() {
        when(repository.findByTelegramUserId(42L)).thenReturn(Optional.empty());
        assertThat(repository.findByTelegramUserId(42L)).isEmpty();
    }

    private EnsureUserUseCaseResult call() {
        var r = service.ensureUser(PRINCIPAL);
        return new EnsureUserUseCaseResult(r.user(), r.registered(), r.blocked());
    }

    private static User user(UUID id, UserStatus status) {
        Instant now = Instant.now();
        return new User(id, 42L, "dmitrii", "Dmitrii", "ru", now, now, status);
    }

    private record EnsureUserUseCaseResult(
            ru.smartbot.application.api.user.UserView user, boolean registered, boolean blocked) {
    }
}
