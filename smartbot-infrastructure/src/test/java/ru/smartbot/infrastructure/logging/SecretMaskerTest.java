package ru.smartbot.infrastructure.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Прямые unit-тесты SecretMasker (AD-4, Story 1.1): regression на каждый
 * закрытый bypass плюс fail-closed/идемпотентность. Все секреты — фиктивные.
 */
class SecretMaskerTest {

    private static final String FAKE_BOT_TOKEN = "bot123456:ABC-DEF_x";
    private static final String FAKE_JWT =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";

    @Test
    @DisplayName("Escaped JSON: \\\"password\\\":\\\"hunter2\\\" маскируется, структура сохраняется")
    void masksEscapedJsonPassword() {
        String masked = SecretMasker.mask("{\\\"password\\\":\\\"hunter2\\\",\\\"user\\\":\\\"42\\\"}");

        assertThat(masked)
                .isEqualTo("{\\\"password\\\":\\\"[REDACTED]\\\",\\\"user\\\":\\\"42\\\"}")
                .doesNotContain("hunter2");
    }

    @Test
    @DisplayName("Escaped JSON: \\\"Authorization\\\":\\\"Bearer …\\\" маскируется целиком")
    void masksEscapedJsonAuthorization() {
        String masked = SecretMasker.mask("{\\\"Authorization\\\":\\\"Bearer " + FAKE_JWT + "\\\"}");

        assertThat(masked)
                .isEqualTo("{\\\"Authorization\\\":\\\"[REDACTED]\\\"}")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "api-key=ak-fake-123",
            "X-Api-Key: ak-fake-123",
            "x-api-key=ak-fake-123",
            "\"api-key\": \"ak-fake-123\""
    })
    @DisplayName("Hyphenated ключи api-key / X-Api-Key маскируются регистронезависимо")
    void masksHyphenatedApiKey(String input) {
        assertThat(SecretMasker.mask(input))
                .contains("[REDACTED]")
                .doesNotContain("ak-fake-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "private_key=base64-fake-key",
            "id_token=jwt.fake.value",
            "bot_token=123456:ABC-DEF_fake",
            "token=bare-fake-token",
            "passwd=hunter2",
            "pwd=hunter2",
            "\"id_token\":\"jwt.fake.value\"",
            "privateKey=base64-fake-key",
            "idToken=jwt.fake.value",
            "botToken=123456:ABC-DEF_fake"
    })
    @DisplayName("Дополнительные секретные ключи (private_key, id_token, bot_token, bare token, passwd, pwd)")
    void masksAdditionalSecretKeys(String input) {
        assertThat(SecretMasker.mask(input))
                .contains("[REDACTED]")
                .doesNotContain("base64-fake-key", "jwt.fake.value", "ABC-DEF_fake", "bare-fake-token", "hunter2");
    }

    @Test
    @DisplayName("Standalone Bearer <jwt> без префикса Authorization маскируется")
    void masksStandaloneBearer() {
        assertThat(SecretMasker.mask("upstream said: Bearer " + FAKE_JWT + " (expired)"))
                .isEqualTo("upstream said: Bearer [REDACTED] (expired)")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    @DisplayName("Standalone Basic <b64> без префикса Authorization маскируется")
    void masksStandaloneBasic() {
        assertThat(SecretMasker.mask("header value Basic dGVzdC1rZXk="))
                .isEqualTo("header value Basic [REDACTED]")
                .doesNotContain("dGVzdC1rZXk=");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "postgres://smartbot:hunter2@db.internal:5432/smartbot",
            "https://user:hunter2@api.example.com/v1",
            "jdbc:postgresql://smartbot:hunter2@db.internal:5432/smartbot"
    })
    @DisplayName("Credentials в URL (scheme://user:pass@host) маскируются")
    void masksUrlCredentials(String input) {
        assertThat(SecretMasker.mask(input))
                .contains(":[REDACTED]@")
                .doesNotContain("hunter2");
    }

    @Test
    @DisplayName("JDBC URL с параметром password= маскируется, остальные параметры целы")
    void masksJdbcPasswordParameter() {
        String masked = SecretMasker.mask("jdbc:postgresql://db:5432/app?password=hunter2&ssl=true");

        assertThat(masked)
                .isEqualTo("jdbc:postgresql://db:5432/app?password=[REDACTED]&ssl=true")
                .doesNotContain("hunter2");
    }

    @Test
    @DisplayName("URL без credentials и с портом не искажается")
    void leavesCredentialFreeUrlsUnchanged() {
        assertThat(SecretMasker.mask("GET https://api.example.com:443/v1/status ok"))
                .isEqualTo("GET https://api.example.com:443/v1/status ok");
    }

    @Test
    @DisplayName("PGP PRIVATE KEY BLOCK маскируется целиком")
    void masksPgpPrivateKeyBlock() {
        String pgp = "-----BEGIN PGP PRIVATE KEY BLOCK-----\n"
                + "Version: fake\n"
                + "\n"
                + "lQOYBFakeFAKEfakeFAKE\n"
                + "-----END PGP PRIVATE KEY BLOCK-----";

        assertThat(SecretMasker.mask("key dump: " + pgp + " (end)"))
                .isEqualTo("key dump: [REDACTED PRIVATE KEY] (end)")
                .doesNotContain("lQOYBFake", "BEGIN PGP PRIVATE KEY");
    }

    @Test
    @DisplayName("Значение с пробелами в кавычках маскируется целиком")
    void masksQuotedValueWithSpaces() {
        assertThat(SecretMasker.mask("password=\"my secret passphrase\""))
                .isEqualTo("password=\"[REDACTED]\"")
                .doesNotContain("my secret passphrase");
        assertThat(SecretMasker.mask("{\"secret\": \"two words\"}"))
                .isEqualTo("{\"secret\": \"[REDACTED]\"}");
    }

    @Test
    @DisplayName("Folded Authorization: продолжение с отступом на следующей строке маскируется")
    void masksFoldedAuthorization() {
        String folded = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9\r\n"
                + "    .eyJzdWIiOiIxMjM0NTY3ODkwIn0\r\n"
                + "X-Request-Id: 42";

        assertThat(SecretMasker.mask(folded))
                .isEqualTo("Authorization: [REDACTED]\r\nX-Request-Id: 42")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9", "eyJzdWIiOiIxMjM0NTY3ODkwIn0");
    }

    @Test
    @DisplayName("Folded-логика не съедает stacktrace-кадры после сообщения с Authorization")
    void foldedMaskingPreservesStackFrames() {
        String trace = "java.lang.RuntimeException: Authorization: Bearer xyz-secret\r\n"
                + "\tat ru.smartbot.Fake.run(Fake.java:1)\r\n"
                + "\tat ru.smartbot.Fake.main(Fake.java:2)";

        assertThat(SecretMasker.mask(trace))
                .isEqualTo("java.lang.RuntimeException: Authorization: [REDACTED]\r\n"
                        + "\tat ru.smartbot.Fake.run(Fake.java:1)\r\n"
                        + "\tat ru.smartbot.Fake.main(Fake.java:2)")
                .doesNotContain("xyz-secret");
    }

    @Test
    @DisplayName("PEM-маскирование идемпотентно (полный и усечённый блок)")
    void pemMaskingIsIdempotent() {
        String pem = "-----BEGIN PRIVATE KEY-----\nMIIEfakeFAKE\n-----END PRIVATE KEY-----";
        String cut = "log cut: -----BEGIN RSA PRIVATE KEY-----\nMIIEpartial";

        String once = SecretMasker.mask("key: " + pem + " / " + cut);
        assertThat(SecretMasker.mask(once)).isEqualTo(once);
        assertThat(once).doesNotContain("MIIEfakeFAKE", "MIIEpartial");
    }

    @Test
    @DisplayName("Unterminated PEM — fail-closed: всё после BEGIN маскируется, включая безобидный хвост")
    void unterminatedPemIsFailClosed() {
        String masked = SecretMasker.mask("-----BEGIN PRIVATE KEY-----\nMIIEpartial\ntrailing innocent text");

        assertThat(masked)
                .isEqualTo("[REDACTED PRIVATE KEY]")
                .doesNotContain("MIIEpartial", "trailing innocent text");
    }

    @Test
    @DisplayName("bot token маскируется во всех контекстах")
    void masksBotToken() {
        assertThat(SecretMasker.mask("GET https://api.telegram.org/" + FAKE_BOT_TOKEN + "/sendMessage"))
                .isEqualTo("GET https://api.telegram.org/bot[REDACTED]/sendMessage")
                .doesNotContain("ABC-DEF_x", "bot123456:");
    }

    @Test
    @DisplayName("null/пустая строка возвращаются как есть, чистый текст не изменяется")
    void handlesNullEmptyAndCleanText() {
        assertThat(SecretMasker.mask(null)).isNull();
        assertThat(SecretMasker.mask("")).isEmpty();
        String clean = "Пользователь 42 отправил inline-запрос, ответ доставлен";
        assertThat(SecretMasker.mask(clean)).isEqualTo(clean);
    }

    @Test
    @DisplayName("Комбинированное маскирование идемпотентно")
    void combinedMaskingIsIdempotent() {
        String kitchenSink = "url=" + FAKE_BOT_TOKEN
                + " password=hunter2 {\"access_token\":\"abc\"}"
                + " postgres://user:hunter2@db:5432/app"
                + " -----BEGIN PRIVATE KEY-----\nfake\n-----END PRIVATE KEY-----"
                + " Authorization: Bearer " + FAKE_JWT;

        String once = SecretMasker.mask(kitchenSink);
        assertThat(SecretMasker.mask(once)).isEqualTo(once);
        assertThat(once).doesNotContain("hunter2", "ABC-DEF_x", "eyJhbGciOiJIUzI1NiJ9", "\"abc\"", "fake\n");
    }
}
