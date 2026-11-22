package ru.smartbot.domain.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DomainException несёт только ErrorCode + безопасные типизированные параметры;
 * сырые payload не принимаются конструктором (AD-20). Unit без Spring.
 */
class DomainExceptionTest {

    @Test
    @DisplayName("Несёт код и безопасные параметры")
    void carriesCodeAndSafeParameters() {
        DomainException exception = new DomainException(
                ErrorCode.MESSAGE_TOO_LONG, Map.of("limit", "4096"));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.MESSAGE_TOO_LONG);
        assertThat(exception.getParameters()).containsExactlyEntriesOf(Map.of("limit", "4096"));
    }

    @Test
    @DisplayName("Без параметров — пустая неизменяемая мапа")
    void defaultsToEmptyParameters() {
        DomainException exception = new DomainException(ErrorCode.EXPIRED);

        assertThat(exception.getCode()).isEqualTo(ErrorCode.EXPIRED);
        assertThat(exception.getParameters()).isEmpty();
    }

    @Test
    @DisplayName("getMessage() — только имя кода, без сырых деталей")
    void messageIsOnlyCodeName() {
        DomainException exception = new DomainException(
                ErrorCode.PROVIDER_UNAVAILABLE, Map.of("provider", "gigachat"));

        assertThat(exception.getMessage()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    @DisplayName("Параметры копируются защитно: мутация исходной мапы не влияет на исключение")
    void parametersAreDefensivelyCopied() {
        Map<String, String> source = new HashMap<>();
        source.put("telegramUserId", "42");
        DomainException exception = new DomainException(ErrorCode.VALIDATION, source);

        source.put("telegramUserId", "999");
        source.put("rawBody", "{\"error\":\"secret payload\"}");

        assertThat(exception.getParameters()).containsExactlyEntriesOf(Map.of("telegramUserId", "42"));
        assertThatThrownBy(() -> exception.getParameters().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Null-код и null-параметры отвергаются fail-fast")
    void rejectsNulls() {
        assertThatThrownBy(() -> new DomainException(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DomainException(ErrorCode.INTERNAL, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Null-ключи и null-значения в параметрах отвергаются fail-fast (NPE)")
    void rejectsNullParameterKeysAndValues() {
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("limit", null);
        assertThatThrownBy(() -> new DomainException(ErrorCode.VALIDATION, nullValue))
                .isInstanceOf(NullPointerException.class);

        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "42");
        assertThatThrownBy(() -> new DomainException(ErrorCode.VALIDATION, nullKey))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Serialization round-trip сохраняет код, параметры и сообщение")
    void serializationRoundTripPreservesState() throws Exception {
        DomainException original = new DomainException(
                ErrorCode.DELIVERY_FAILED, Map.of("telegramUserId", "42"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        DomainException restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (DomainException) in.readObject();
        }

        assertThat(restored.getCode()).isEqualTo(ErrorCode.DELIVERY_FAILED);
        assertThat(restored.getParameters()).containsExactlyEntriesOf(Map.of("telegramUserId", "42"));
        assertThat(restored.getMessage()).isEqualTo("DELIVERY_FAILED");
    }
}