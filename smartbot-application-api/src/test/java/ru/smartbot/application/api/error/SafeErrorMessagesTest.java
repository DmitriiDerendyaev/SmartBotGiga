package ru.smartbot.application.api.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.smartbot.domain.error.ErrorCode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Полнота таблицы ErrorCode→безопасный текст и безопасность текстов (AD-20).
 * Unit без Spring.
 */
class SafeErrorMessagesTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("Каждый из 15 кодов покрыт непустым безопасным текстом на русском")
    void everyCodeHasNonEmptyRussianText(ErrorCode code) {
        String text = SafeErrorMessages.messageFor(code);

        assertThat(text).isNotBlank();
        assertThat(text).matches(".*[А-Яа-яЁё].*");
    }

    @Test
    @DisplayName("Таблица покрывает весь каталог без пропусков")
    void tableCoversWholeCatalog() {
        Set<String> texts = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            texts.add(SafeErrorMessages.messageFor(code));
        }

        assertThat(ErrorCode.values()).hasSize(15);
        // Project convention (не требование AD-20): у каждого кода свой текст,
        // чтобы ситуации были различимы для пользователя; объединение текстов —
        // осознанное изменение конвенции, а не случайность.
        assertThat(texts).hasSize(ErrorCode.values().length);
    }

    @Test
    @DisplayName("Тексты не содержат внутренних деталей: имён провайдеров, exception-классов, URL")
    void textsHaveNoInternalDetails() {
        for (ErrorCode code : ErrorCode.values()) {
            String text = SafeErrorMessages.messageFor(code);

            assertThat(text)
                    .doesNotContain("Exception", "http", "GigaChat", "gigachat", "Telegram", "telegram")
                    // код — служебный, пользователю не показывается; регистронезависимо,
                    // чтобы «Validation»/«validation» в тексте тоже не проскочили
                    .doesNotContainIgnoringCase(code.name());
        }
    }

    @Test
    @DisplayName("Null-код отвергается fail-fast")
    void rejectsNullCode() {
        assertThatThrownBy(() -> SafeErrorMessages.messageFor(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Все значения каталога AD-20 присутствуют (защита от расхождения с domain)")
    void catalogMatchesAd20() {
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
                .containsExactly(
                        "VALIDATION", "INVALID_CALLBACK", "EXPIRED", "BLOCKED", "RATE_LIMITED",
                        "QUOTA_EXCEEDED", "BUDGET_EXCEEDED", "TIMEOUT", "PROVIDER_UNAVAILABLE",
                        "AUTH", "SEARCH_UNAVAILABLE", "DELIVERY_FAILED", "MESSAGE_TOO_LONG",
                        "RESTARTED", "INTERNAL");
    }
}