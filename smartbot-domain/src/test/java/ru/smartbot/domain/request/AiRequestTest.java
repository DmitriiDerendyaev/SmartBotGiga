package ru.smartbot.domain.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiRequest — переходы и терминальность (AD-15)")
class AiRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    @Test
    @DisplayName("CREATED → GENERATING → COMPLETED; completedAt выставляется")
    void happyPath() {
        AiRequest created = request(AiRequestStatus.CREATED);
        AiRequest generating = created.transitionTo(AiRequestStatus.GENERATING, NOW);
        AiRequest completed = generating.transitionTo(AiRequestStatus.COMPLETED, NOW.plusSeconds(5));

        assertThat(generating.status()).isEqualTo(AiRequestStatus.GENERATING);
        assertThat(generating.completedAt()).isNull();
        assertThat(completed.status()).isEqualTo(AiRequestStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(completed.id()).isEqualTo(created.id());
    }

    @Test
    @DisplayName("недопустимый переход отклоняется")
    void invalidTransition() {
        assertThatThrownBy(() -> request(AiRequestStatus.CREATED)
                .transitionTo(AiRequestStatus.COMPLETED, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("терминальный статус нельзя изменить")
    void terminalImmutable() {
        assertThatThrownBy(() -> request(AiRequestStatus.COMPLETED)
                .transitionTo(AiRequestStatus.GENERATING, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("token длиннее 64 символов отвергается (AD-13)")
    void tokenTooLong() {
        assertThatThrownBy(() -> new AiRequest(UUID.randomUUID(), UUID.randomUUID(), null, 1L,
                null, "x".repeat(65), "q", AiRequestStatus.CREATED, null, null, false,
                null, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("lease истёк — кандидат recovery")
    void leaseExpiry() {
        AiRequest withLease = new AiRequest(UUID.randomUUID(), UUID.randomUUID(), null, 1L,
                null, "g:abc", "q", AiRequestStatus.GENERATING, null, null, false,
                null, null, NOW.minusSeconds(1), NOW, null);
        assertThat(withLease.isLeaseExpired(NOW)).isTrue();
    }

    private static AiRequest request(AiRequestStatus status) {
        return new AiRequest(UUID.randomUUID(), UUID.randomUUID(), null, 1L, null,
                "g:" + UUID.randomUUID().toString().replace("-", ""), "query", status,
                null, null, false, null, null, null, NOW, null);
    }
}
