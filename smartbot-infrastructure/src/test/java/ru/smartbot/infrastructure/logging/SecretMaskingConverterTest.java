package ru.smartbot.infrastructure.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression маскирования секретов по I/O-матрице Story 1.1 (AD-4).
 * Все секреты в тестах — фиктивные. Unit без Spring.
 */
class SecretMaskingConverterTest {

    private static final String FAKE_BOT_TOKEN = "bot123456:ABC-DEF_x";

    private final SecretMaskingConverter converter = new SecretMaskingConverter();

    private String maskMessage(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        return converter.convert(event);
    }

    @Nested
    @DisplayName("Сообщение (%maskedMsg)")
    class MessageMasking {

        @Test
        @DisplayName("Bot token в URL Telegram API → bot[REDACTED]")
        void masksBotToken() {
            String masked = maskMessage("GET https://api.telegram.org/" + FAKE_BOT_TOKEN + "/getUpdates failed");

            assertThat(masked)
                    .isEqualTo("GET https://api.telegram.org/bot[REDACTED]/getUpdates failed")
                    .doesNotContain("ABC-DEF_x", "bot123456:");
        }

        @Test
        @DisplayName("Authorization: Bearer — значение маскируется регистронезависимо")
        void masksAuthorizationHeader() {
            assertThat(maskMessage("Authorization: Bearer xyz-secret"))
                    .isEqualTo("Authorization: [REDACTED]")
                    .doesNotContain("xyz-secret");
            assertThat(maskMessage("authorization: bearer abc"))
                    .isEqualTo("authorization: [REDACTED]");
            assertThat(maskMessage("AUTHORIZATION=Basic dGVzdC1rZXk="))
                    .isEqualTo("AUTHORIZATION=[REDACTED]");
        }

        @Test
        @DisplayName("JSON-поля access_token/refresh_token маскируются")
        void masksJsonTokens() {
            String masked = maskMessage("{\"access_token\":\"abc\",\"refresh_token\":\"def\",\"expires_in\":3600}");

            assertThat(masked)
                    .isEqualTo("{\"access_token\":\"[REDACTED]\",\"refresh_token\":\"[REDACTED]\",\"expires_in\":3600}")
                    .doesNotContain("abc", "def");
        }

        @Test
        @DisplayName("password/secret/api_key маскируются в form- и JSON-представлении")
        void masksKeyValueSecrets() {
            assertThat(maskMessage("password=hunter2&user=42"))
                    .isEqualTo("password=[REDACTED]&user=42");
            assertThat(maskMessage("\"secret\": \"s3cr3t-value\""))
                    .isEqualTo("\"secret\": \"[REDACTED]\"");
            assertThat(maskMessage("api_key: AKIAFAKEKEY123"))
                    .isEqualTo("api_key: [REDACTED]");
            assertThat(maskMessage("client_secret=cs-fake"))
                    .isEqualTo("client_secret=[REDACTED]");
            assertThat(maskMessage("accessToken=camel-fake"))
                    .isEqualTo("accessToken=[REDACTED]");
        }

        @Test
        @DisplayName("PrivateKey блок (multiline) целиком → [REDACTED PRIVATE KEY]")
        void masksPrivateKeyBlock() {
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDFAKEfakeFAKE\n"
                    + "-----END PRIVATE KEY-----";

            assertThat(maskMessage("key dump: " + pem + " (end)"))
                    .isEqualTo("key dump: [REDACTED PRIVATE KEY] (end)")
                    .doesNotContain("MIIEvQ", "BEGIN PRIVATE KEY");
        }

        @Test
        @DisplayName("RSA/ENCRYPTED варианты и усечённый блок без END тоже маскируются")
        void masksPrivateKeyVariants() {
            assertThat(maskMessage("-----BEGIN RSA PRIVATE KEY-----\nfakebase64\n-----END RSA PRIVATE KEY-----"))
                    .isEqualTo("[REDACTED PRIVATE KEY]");
            assertThat(maskMessage("-----BEGIN ENCRYPTED PRIVATE KEY-----\nfake\n-----END ENCRYPTED PRIVATE KEY-----"))
                    .isEqualTo("[REDACTED PRIVATE KEY]");
            assertThat(maskMessage("log cut: -----BEGIN PRIVATE KEY-----\nMIIEpartial"))
                    .isEqualTo("log cut: [REDACTED PRIVATE KEY]")
                    .doesNotContain("MIIEpartial");
        }

        @Test
        @DisplayName("Чистое сообщение без секретов не изменяется")
        void leavesCleanMessageUnchanged() {
            String clean = "Пользователь 42 отправил inline-запрос, ответ доставлен";

            assertThat(maskMessage(clean)).isEqualTo(clean);
        }

        @Test
        @DisplayName("null и пустое сообщение возвращаются как есть")
        void handlesNullAndEmpty() {
            assertThat(maskMessage(null)).isNull();
            assertThat(maskMessage("")).isEmpty();
        }

        @Test
        @DisplayName("Секреты в форматированных аргументах log.info(\"token {}\", secret) маскируются")
        void masksSecretsInParameterizedArguments() {
            LoggingEvent event = new LoggingEvent();
            event.setMessage("token {}");
            event.setArgumentArray(new Object[]{FAKE_BOT_TOKEN});

            assertThat(converter.convert(event)).isEqualTo("token bot[REDACTED]");
        }

        @Test
        @DisplayName("Маскирование идемпотентно")
        void maskingIsIdempotent() {
            String once = maskMessage("Authorization: Bearer xyz " + FAKE_BOT_TOKEN + " password=hunter2");

            assertThat(maskMessage(once)).isEqualTo(once);
        }
    }

    @Nested
    @DisplayName("Stacktrace (%maskedEx)")
    class StacktraceMasking {

        private final MaskingThrowableProxyConverter converter = new MaskingThrowableProxyConverter();

        @Test
        @DisplayName("Секреты в сообщении исключения и cause-цепочке маскируются")
        void masksSecretsAcrossCauseChain() {
            RuntimeException cause = new IllegalStateException("Authorization: Bearer cause-token");
            RuntimeException top = new RuntimeException("call failed for " + FAKE_BOT_TOKEN, cause);
            LoggingEvent event = new LoggingEvent();
            event.setThrowableProxy(new ThrowableProxy(top));
            converter.start();

            String rendered = converter.convert(event);

            assertThat(rendered)
                    .contains("bot[REDACTED]", "Authorization: [REDACTED]", "Caused by:")
                    .doesNotContain("ABC-DEF_x", "cause-token");
        }

        @Test
        @DisplayName("Секреты в suppressed-исключениях маскируются")
        void masksSuppressedExceptions() {
            RuntimeException top = new RuntimeException("primary failure");
            top.addSuppressed(new IllegalStateException("password=suppressed-fake"));
            LoggingEvent event = new LoggingEvent();
            event.setThrowableProxy(new ThrowableProxy(top));
            converter.start();

            String rendered = converter.convert(event);

            assertThat(rendered)
                    .contains("Suppressed:", "password=[REDACTED]")
                    .doesNotContain("suppressed-fake");
        }

        @Test
        @DisplayName("Структура stacktrace сохраняется: классы и кадры на месте")
        void preservesStacktraceStructure() {
            LoggingEvent event = new LoggingEvent();
            event.setThrowableProxy(new ThrowableProxy(new RuntimeException("boom")));
            converter.start();

            String rendered = converter.convert(event);

            assertThat(rendered)
                    .contains("java.lang.RuntimeException: boom")
                    .contains("at " + SecretMaskingConverterTest.class.getName());
        }

        @Test
        @DisplayName("Без исключения — пустой вывод")
        void emptyWithoutThrowable() {
            converter.start();

            assertThat(converter.convert(new LoggingEvent())).isEmpty();
        }
    }

    @Nested
    @DisplayName("MDC (%mdc/%X)")
    class MdcMasking {

        private final MaskingMdcConverter converter = new MaskingMdcConverter();

        @Test
        @DisplayName("Секреты в значениях MDC маскируются до вывода")
        void masksMdcValues() {
            LoggingEvent event = new LoggingEvent();
            event.setMDCPropertyMap(Map.of(
                    "botToken", FAKE_BOT_TOKEN,
                    "password", "hunter2"));
            converter.start();

            String rendered = converter.convert(event);

            assertThat(rendered)
                    .contains("botToken=[REDACTED]", "password=[REDACTED]")
                    .doesNotContain("ABC-DEF_x", "hunter2");
        }

        @Test
        @DisplayName("%X{key}: bare-значение Bearer маскируется без префикса Authorization")
        void masksBareBearerValueForSingleKey() {
            MaskingMdcConverter singleKey = new MaskingMdcConverter();
            singleKey.setOptionList(List.of("authHeader"));
            singleKey.start();
            LoggingEvent event = new LoggingEvent();
            event.setMDCPropertyMap(Map.of("authHeader", "Bearer eyJhbGciOiJIUzI1NiJ9.fake.signature"));

            assertThat(singleKey.convert(event))
                    .isEqualTo("Bearer [REDACTED]")
                    .doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        }

        @Test
        @DisplayName("%X{key}: bare-значение Basic и bot-token формы маскируются")
        void masksBareBasicAndBotTokenValuesForSingleKey() {
            LoggingEvent basic = new LoggingEvent();
            basic.setMDCPropertyMap(Map.of("authHeader", "Basic dGVzdC1rZXk="));
            MaskingMdcConverter basicKey = new MaskingMdcConverter();
            basicKey.setOptionList(List.of("authHeader"));
            basicKey.start();
            assertThat(basicKey.convert(basic)).isEqualTo("Basic [REDACTED]");

            LoggingEvent botToken = new LoggingEvent();
            botToken.setMDCPropertyMap(Map.of("botToken", FAKE_BOT_TOKEN));
            MaskingMdcConverter botTokenKey = new MaskingMdcConverter();
            botTokenKey.setOptionList(List.of("botToken"));
            botTokenKey.start();
            assertThat(botTokenKey.convert(botToken)).isEqualTo("bot[REDACTED]");
        }

        @Test
        @DisplayName("Чистый MDC не изменяется")
        void leavesCleanMdcUnchanged() {
            LoggingEvent event = new LoggingEvent();
            event.setMDCPropertyMap(Map.of("updateId", "42"));
            converter.start();

            assertThat(converter.convert(event)).isEqualTo("updateId=42");
        }
    }

    @Test
    @DisplayName("Конвертер сообщения совместим с контрактом logback ClassicConverter")
    void converterFulfilsLogbackContract() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("token " + FAKE_BOT_TOKEN);

        assertThat(converter.convert((ILoggingEvent) event)).contains("bot[REDACTED]");
    }
}