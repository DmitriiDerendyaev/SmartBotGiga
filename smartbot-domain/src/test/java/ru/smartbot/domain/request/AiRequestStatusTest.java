package ru.smartbot.domain.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiRequestStatus — граф переходов (AD-15)")
class AiRequestStatusTest {

    @ParameterizedTest
    @EnumSource(AiRequestStatus.class)
    @DisplayName("CREATED допускает только GENERATING/EXPIRED/FAILED")
    void createdTransitions(AiRequestStatus target) {
        boolean allowed = target == AiRequestStatus.GENERATING
                || target == AiRequestStatus.EXPIRED
                || target == AiRequestStatus.FAILED;
        assertThat(AiRequestStatus.CREATED.canTransitionTo(target)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(AiRequestStatus.class)
    @DisplayName("GENERATING допускает только COMPLETED/FAILED/EXPIRED")
    void generatingTransitions(AiRequestStatus target) {
        boolean allowed = target == AiRequestStatus.COMPLETED
                || target == AiRequestStatus.FAILED
                || target == AiRequestStatus.EXPIRED;
        assertThat(AiRequestStatus.GENERATING.canTransitionTo(target)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(AiRequestStatus.class)
    @DisplayName("терминальные статусы immutable (AD-15)")
    void terminalIsImmutable(AiRequestStatus target) {
        for (AiRequestStatus terminal : new AiRequestStatus[]{
                AiRequestStatus.COMPLETED, AiRequestStatus.FAILED, AiRequestStatus.EXPIRED}) {
            assertThat(terminal.isTerminal()).isTrue();
            assertThat(terminal.canTransitionTo(target)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(AiRequestStatus.class)
    @DisplayName("активны только CREATED/GENERATING")
    void activeStatuses(AiRequestStatus status) {
        assertThat(status.isActive())
                .isEqualTo(status == AiRequestStatus.CREATED || status == AiRequestStatus.GENERATING);
    }
}
