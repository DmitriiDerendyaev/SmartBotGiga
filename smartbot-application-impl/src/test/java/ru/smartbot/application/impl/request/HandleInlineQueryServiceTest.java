package ru.smartbot.application.impl.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.application.api.request.HandleInlineQueryUseCase;
import ru.smartbot.application.api.request.ProcessedUpdateRepository;
import ru.smartbot.application.api.user.EnsureUserUseCase;
import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.application.api.user.UserView;
import ru.smartbot.domain.error.DomainException;
import ru.smartbot.domain.error.ErrorCode;
import ru.smartbot.domain.request.AiRequest;
import ru.smartbot.domain.request.AiRequestStatus;
import ru.smartbot.domain.user.UserStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HandleInlineQueryService — I/O-матрица story 1.3")
class HandleInlineQueryServiceTest {

    private final ProcessedUpdateRepository ledger = mock(ProcessedUpdateRepository.class);
    private final EnsureUserUseCase ensureUser = mock(EnsureUserUseCase.class);
    private final AiRequestRepository aiRequests = mock(AiRequestRepository.class);
    private final CorrelationTokenGenerator tokens = mock(CorrelationTokenGenerator.class);
    private final SmartBotRequestProperties properties = new SmartBotRequestProperties("PT10M", "PT1H", 256);

    private HandleInlineQueryService service;

    private final UUID userId = UUID.randomUUID();
    private final TelegramPrincipal principal = new TelegramPrincipal(42L, "u", "U", "ru");

    @BeforeEach
    void setUp() {
        service = new HandleInlineQueryService(ledger, ensureUser, aiRequests, tokens, properties);
        when(ledger.tryRecord(anyLong(), anyString(), any())).thenReturn(true);
        when(ensureUser.ensureUser(principal)).thenReturn(
                new EnsureUserUseCase.Result(view(UserStatus.ACTIVE), true, false));
        when(tokens.generate()).thenReturn("g:abcdef0123456789abcdef0123456789");
        when(aiRequests.create(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiRequests.findActiveByUserAndQuery(any(), anyString(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("новый query: создаёт CREATED с token ≤64, не duplicate/reused")
    void newQueryCreatesRequest() {
        var result = service.handle(new HandleInlineQueryUseCase.Command(1L, principal, "  Hello  "));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.reused()).isFalse();
        assertThat(result.correlationToken()).hasSizeLessThanOrEqualTo(64);
        verify(aiRequests).create(any());
    }

    @Test
    @DisplayName("дубль update_id: ledger гасит, AiRequest не создаётся")
    void duplicateUpdate() {
        when(ledger.tryRecord(eq(2L), anyString(), any())).thenReturn(false);

        var result = service.handle(new HandleInlineQueryUseCase.Command(2L, principal, "Hello"));

        assertThat(result.duplicate()).isTrue();
        verify(aiRequests, never()).create(any());
    }

    @Test
    @DisplayName("blocked: отказ BLOCKED до создания AiRequest")
    void blockedUser() {
        when(ensureUser.ensureUser(principal)).thenReturn(
                new EnsureUserUseCase.Result(view(UserStatus.BLOCKED), false, true));

        assertThatThrownBy(() -> service.handle(
                new HandleInlineQueryUseCase.Command(3L, principal, "Hello")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.BLOCKED);
        verify(aiRequests, never()).create(any());
    }

    @Test
    @DisplayName("повторный query в TTL: переиспользует token, новой записи нет")
    void reuseWithinTtl() {
        AiRequest existing = new AiRequest(UUID.randomUUID(), userId, null, 4L, null,
                "g:existingtoken", "Hello", AiRequestStatus.CREATED, null, null, false,
                null, null, null, Instant.now(), null);
        when(aiRequests.findActiveByUserAndQuery(eq(userId), eq("Hello"), any()))
                .thenReturn(Optional.of(existing));

        var result = service.handle(new HandleInlineQueryUseCase.Command(5L, principal, "Hello"));

        assertThat(result.reused()).isTrue();
        assertThat(result.correlationToken()).isEqualTo("g:existingtoken");
        verify(aiRequests, never()).create(any());
    }

    @Test
    @DisplayName("слишком длинный query: MESSAGE_TOO_LONG без обрезки")
    void tooLongQuery() {
        String longQuery = "x".repeat(257);

        assertThatThrownBy(() -> service.handle(
                new HandleInlineQueryUseCase.Command(6L, principal, longQuery)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.MESSAGE_TOO_LONG);
        verify(aiRequests, never()).create(any());
    }

    @Test
    @DisplayName("пустой query: VALIDATION")
    void blankQuery() {
        assertThatThrownBy(() -> service.handle(
                new HandleInlineQueryUseCase.Command(7L, principal, "   ")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.VALIDATION);
    }

    private UserView view(UserStatus status) {
        Instant now = Instant.now();
        return new UserView(userId, 42L, "u", "U", "ru", status, now, now);
    }
}
