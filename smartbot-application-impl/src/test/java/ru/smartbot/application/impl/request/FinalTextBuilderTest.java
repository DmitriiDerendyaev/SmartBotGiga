package ru.smartbot.application.impl.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.smartbot.application.api.telegram.MessageLimits;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FinalTextBuilder — HTML, обрезка, sources не теряются (AD-19)")
class FinalTextBuilderTest {

    private final FinalTextBuilder builder = new FinalTextBuilder();

    @Test
    @DisplayName("HTML экранируется")
    void escapesHtml() {
        var text = builder.build("<script>x</script> & co", List.of());
        assertThat(text.sentText()).contains("&lt;script&gt;").doesNotContain("<script>");
        assertThat(text.truncated()).isFalse();
    }

    @Test
    @DisplayName("длинный ответ обрезается до 4096, в БД остаётся полный")
    void truncatesLongBody() {
        var text = builder.build("x".repeat(MessageLimits.MAX_MESSAGE_LENGTH + 500), List.of());

        assertThat(text.sentText().length()).isLessThanOrEqualTo(MessageLimits.MAX_MESSAGE_LENGTH);
        assertThat(text.sentText()).endsWith("…");
        assertThat(text.fullText().length()).isGreaterThan(MessageLimits.MAX_MESSAGE_LENGTH);
        assertThat(text.truncated()).isTrue();
    }

    @Test
    @DisplayName("sources-блок сохраняется целиком даже при обрезке тела")
    void keepsSourcesOnTruncation() {
        String url = "https://example.com/a";
        var text = builder.build("y".repeat(5000), List.of(url));

        assertThat(text.sentText()).contains(url);
        assertThat(text.sentText().length()).isLessThanOrEqualTo(MessageLimits.MAX_MESSAGE_LENGTH);
    }
}
