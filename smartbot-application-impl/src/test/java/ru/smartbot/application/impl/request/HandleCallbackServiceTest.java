package ru.smartbot.application.impl.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.application.api.request.HandleCallbackUseCase;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HandleCallbackService — валидация callback (AD-8/13/14/15/17)")
class HandleCallbackServiceTest {

    private final ProcessedUpdateRepository ledger = mock(ProcessedUpdateRepository.class);
    private final EnsureUserUseCase ensureUser = mock(EnsureUserUseCase.class);
    private final AiRequestRepository aiRequests = mock(AiRequestRepository.class);
    private final SmartBotRequestProperties properties =
            new SmartBotRequestProperties("PT10M", "PT1H", 256);

    private HandleCallbackService service;

    private final UUID userId = UUID.randomUUID();
    private final TelegramPrincipal principal = new TelegramPrincipal(42L, "u", "U", "ru");
    private static final String TOKEN = "g:token";
    private static final String INLINE_ID = "im-1";

    @BeforeEach
    void setUp() {
        service = new HandleCallbackService(ledger, ensureUser, aiRequests, properties);
        when(ledger.tryRecord(anyLong(), anyString(), any())).thenReturn(true);
        when(ensureUser.ensureUser(principal)).thenReturn(
                new EnsureUserUseCase.Result(view(UserStatus.ACTIVE), false, false));
    }

    @Test
    @DisplayName("валидный callback: привязывает сообщение и возвращает активный запрос")
    void validCallback() {
        when(aiRequests.findByCorrelationToken(TOKEN))
                .thenReturn(Optional.of(request(AiRequestStatus.CREATED, null, Instant.now())));
        when(aiRequests.attachInlineMessageId(any(), anyString())).thenReturn(true);

        var result = service.handle(command(INLINE_ID));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.reusable()).isTrue();
        assertThat(result.status()).isEqualTo(AiRequestStatus.CREATED);
    }

    @Test
    @DisplayName("дубль update: ledger гасит, без обращения к репозиторию")
    void duplicateUpdate() {
        when(ledger.tryRecord(anyLong(), anyString(), any())).thenReturn(false);

        var result = service.handle(command(INLINE_ID));

        assertThat(result.duplicate()).isTrue();
        verify(aiRequests, never()).findByCorrelationToken(anyString());
    }

    @Test
    @DisplayName("blocked: отказ до валидации токена")
    void blockedUser() {
        when(ensureUser.ensureUser(principal)).thenReturn(
                new EnsureUserUseCase.Result(view(UserStatus.BLOCKED), false, true));

        assertThatThrownBy(() -> service.handle(command(INLINE_ID)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.BLOCKED);
    }

    @Test
    @DisplayName("callback без inline_message_id: INVALID_CALLBACK")
    void missingInlineMessageId() {
        assertThatThrownBy(() -> service.handle(command(" ")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_CALLBACK);
    }

    @Test
    @DisplayName("неизвестный token: INVALID_CALLBACK")
    void unknownToken() {
        when(aiRequests.findByCorrelationToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(command(INLINE_ID)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_CALLBACK);
    }

    @Test
    @DisplayName("чужой запрос: INVALID_CALLBACK")
    void foreignOwnership() {
        when(aiRequests.findByCorrelationToken(TOKEN))
                .thenReturn(Optional.of(request(AiRequestStatus.CREATED, null, Instant.now())));

        var foreign = new TelegramPrincipal(999L, "x", "X", "ru");
        when(ensureUser.ensureUser(foreign)).thenReturn(
                new EnsureUserUseCase.Result(view(UserStatus.ACTIVE), false, false));
        var cmd = new HandleCallbackUseCase.Command(1L, foreign, TOKEN, INLINE_ID);

        assertThatThrownBy(() -> service.handle(cmd))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_CALLBACK);
    }

    @Test
    @DisplayName("истёк TTL: EXPIRED ставит impl через CAS")
    void expiredByTtl() {
        AiRequest stale = request(AiRequestStatus.CREATED, null, Instant.now().minusSeconds(7200));
        when(aiRequests.findByCorrelationToken(TOKEN)).thenReturn(Optional.of(stale));
        when(aiRequests.attachInlineMessageId(any(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.handle(command(INLINE_ID)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.EXPIRED);
        verify(aiRequests).transition(org.mockito.ArgumentMatchers.eq(stale.id()),
                org.mockito.ArgumentMatchers.eq(AiRequestStatus.CREATED),
                org.mockito.ArgumentMatchers.eq(AiRequestStatus.EXPIRED),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("сообщение занято другим активным запросом: INVALID_CALLBACK")
    void attachConflict() {
        when(aiRequests.findByCorrelationToken(TOKEN))
                .thenReturn(Optional.of(request(AiRequestStatus.CREATED, null, Instant.now())));
        when(aiRequests.attachInlineMessageId(any(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.handle(command(INLINE_ID)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_CALLBACK);
    }

    private HandleCallbackUseCase.Command command(String inlineMessageId) {
        return new HandleCallbackUseCase.Command(1L, principal, TOKEN, inlineMessageId);
    }

    private AiRequest request(AiRequestStatus status, String inlineMessageId, Instant createdAt) {
        return new AiRequest(UUID.randomUUID(), userId, null, 1L, inlineMessageId, TOKEN, "q",
                status, null, null, false, null, null, null, createdAt, null);
    }

    private UserView view(UserStatus status) {
        Instant now = Instant.now();
        return new UserView(userId, 42L, "u", "U", "ru", status, now, now);
    }
}
