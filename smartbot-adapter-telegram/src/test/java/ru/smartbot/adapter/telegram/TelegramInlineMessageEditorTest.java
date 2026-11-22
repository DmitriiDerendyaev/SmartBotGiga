package ru.smartbot.adapter.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.smartbot.application.api.telegram.InlineMessageEditor.DeliveryResult;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TelegramInlineMessageEditor — классификация ошибок edit (AD-23)")
class TelegramInlineMessageEditorTest {

    @Test
    @DisplayName("message is not modified — идемпотентный успех")
    void notModifiedIsSuccess() {
        assertThat(TelegramInlineMessageEditor.classify(
                "Bad Request: message is not modified: specified new message content and reply markup are exactly the same"))
                .isEqualTo(DeliveryResult.NOT_MODIFIED);
    }

    @Test
    @DisplayName("message to edit not found — сообщение истекло/удалено")
    void notFound() {
        assertThat(TelegramInlineMessageEditor.classify(
                "Bad Request: message to edit not found"))
                .isEqualTo(DeliveryResult.MESSAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("прочие ошибки — FAILED")
    void otherFailure() {
        assertThat(TelegramInlineMessageEditor.classify("Bad Request: chat not found"))
                .isEqualTo(DeliveryResult.FAILED);
        assertThat(TelegramInlineMessageEditor.classify(null)).isEqualTo(DeliveryResult.FAILED);
    }
}
