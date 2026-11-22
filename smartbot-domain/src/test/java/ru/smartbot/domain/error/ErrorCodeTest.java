package ru.smartbot.domain.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Полнота каталога ErrorCode точно по AD-20 (15 значений). Unit без Spring.
 */
class ErrorCodeTest {

    /** Каталог зафиксирован spine AD-20; изменение требует amendment spine. */
    private static final List<String> AD_20_CATALOG = List.of(
            "VALIDATION",
            "INVALID_CALLBACK",
            "EXPIRED",
            "BLOCKED",
            "RATE_LIMITED",
            "QUOTA_EXCEEDED",
            "BUDGET_EXCEEDED",
            "TIMEOUT",
            "PROVIDER_UNAVAILABLE",
            "AUTH",
            "SEARCH_UNAVAILABLE",
            "DELIVERY_FAILED",
            "MESSAGE_TOO_LONG",
            "RESTARTED",
            "INTERNAL");

    @Test
    @DisplayName("Каталог содержит ровно 15 значений AD-20, без добавлений и удалений")
    void catalogIsExactlyAd20() {
        List<String> actual = Arrays.stream(ErrorCode.values()).map(Enum::name).toList();

        assertThat(actual).hasSize(15).containsExactlyElementsOf(AD_20_CATALOG);
    }

    @Test
    @DisplayName("Каждое значение резолвится по имени (персистентность по name, не ordinal)")
    void everyCodeResolvesByName() {
        for (String name : AD_20_CATALOG) {
            assertThat(ErrorCode.valueOf(name)).isNotNull();
        }
    }
}