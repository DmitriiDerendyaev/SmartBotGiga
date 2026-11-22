package ru.smartbot.infrastructure.logging;

import java.util.regex.Pattern;

/**
 * Маскирование секретов в строках логов (AD-4, Story 1.1).
 *
 * <p>Единый набор паттернов для message, stacktrace и MDC — используется
 * всеми masking-конвертерами, чтобы покрытие не расходилось между appender'ами.
 * Только фиктивные примеры в javadoc; реальные секреты в код не попадают.
 *
 * <p>Паттерны:
 * <ul>
 *   <li>bot token Telegram (часть URL API): {@code bot\d+:[A-Za-z0-9_-]+} → {@code bot[REDACTED]};</li>
 *   <li>PEM-блоки PRIVATE KEY, включая PGP PRIVATE KEY BLOCK (multiline, RSA/EC/ENCRYPTED);</li>
 *   <li>Authorization (заголовок/JSON/escaped JSON, включая Bearer/Basic), регистронезависимо,
 *       с поддержкой folded-заголовков (продолжение на следующей строке с отступом);</li>
 *   <li>standalone {@code Bearer <jwt>} / {@code Basic <b64>} без префикса Authorization;</li>
 *   <li>секретные ключи: password/passwd/pwd, secret/client_secret, access_token/refresh_token/
 *       id_token/bot_token, bare token, api_key/api-key (включая X-Api-Key), private_key
 *       (+ camelCase-варианты) — в form-, JSON- и escaped-JSON-представлении
 *       ({@code \"password\":\"hunter2\"}); значения в кавычках могут содержать пробелы;</li>
 *   <li>credentials в URL: {@code scheme://user:pass@host} (включая JDBC-URL);
 *       JDBC-параметр {@code password=} покрывается key-value-паттерном.</li>
 * </ul>
 *
 * <p>Fail-closed: усечённый PEM-блок без END (обрыв лога) маскируется целиком
 * вместе со всем текстом после BEGIN — лучше потерять хвост сообщения, чем
 * раскрыть часть ключа.
 *
 * <p>Известные ограничения:
 * <ul>
 *   <li>escaped-кавычка внутри значения ({@code \"}) трактуется как закрывающий
 *       разделитель: значение с внутренними экранированными кавычками может быть
 *       замаскировано не полностью;</li>
 *   <li>standalone Bearer/Basic маскирует только токеноподобные значения
 *       (≥8 символов base64url/JWT-алфавита); короткие значения и проза
 *       («Basic authentication failed») могут маскироваться с избытком или нет;</li>
 *   <li>folded-продолжение Authorization — любая строка с отступом, кроме
 *       stacktrace-кадров ({@code at ...}, {@code Caused by:}, {@code Suppressed:},
 *       {@code ...}); обычные indented-строки после заголовка поглощаются (fail-closed);</li>
 *   <li>маскирование текстовое: секреты, разбитые на части конкатенацией уже после
 *       форматирования, или закодированные (base64 всего сообщения) не распознаются.</li>
 * </ul>
 */
final class SecretMasker {

    private static final String REDACTED = "[REDACTED]";
    private static final String REDACTED_PRIVATE_KEY = "[REDACTED PRIVATE KEY]";

    /** AD-4: bot token в URL Telegram API, например bot123456:ABC-DEF_x. */
    private static final Pattern BOT_TOKEN = Pattern.compile("bot\\d+:[A-Za-z0-9_-]+");

    /**
     * Секретные ключи. Составные варианты раньше bare-суффиксов
     * (client_secret раньше secret, *_token раньше token), чтобы захватывать
     * ключ целиком. {@code api[_-]key} покрывает и {@code X-Api-Key} (регистронезависимо).
     */
    private static final String SECRET_KEYS =
            "client_secret|access_token|refresh_token|id_token|bot_token|private_key"
                    + "|api[_-]key|accesstoken|refreshtoken|idtoken|bottoken|privatekey|apikey"
                    + "|password|passwd|pwd|secret|token";

    /** Кавычка (опциональная), возможно экранированная: escaped JSON {@code \"key\":\"value\"}. */
    private static final String OPTIONAL_QUOTE = "\\\\?[\"']?";

    /** Символы значения key=value: до whitespace/кавычки/разделителя/обратного слэша. */
    private static final String VALUE_CHAR = "[^\\s\"',;&}\\\\]";

    /** Символы значения заголовка Authorization: пробелы разрешены, до конца строки/разделителя. */
    private static final String HEADER_VALUE_CHAR = "[^\\r\\n\"',;&}\\\\]";

    /**
     * Authorization: значение целиком (включая схему Bearer/Basic) до разделителя,
     * регистронезависимо, в т.ч. в escaped JSON. Поддерживает obs-fold: продолжение
     * на следующей строке с отступом, кроме stacktrace-кадров (иначе маскирование
     * заголовка в сообщении исключения съедало бы кадры stacktrace).
     */
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization" + OPTIONAL_QUOTE + "\\s*[:=]\\s*" + OPTIONAL_QUOTE + ")"
                    + "(" + HEADER_VALUE_CHAR + "*"
                    + "(?:\\r?\\n[ \\t]+(?!(?:at[ \\t]|Caused by:|Suppressed:|\\.\\.\\.))(?=[^ \\t\\r\\n])"
                    + HEADER_VALUE_CHAR + "*)*)");

    /**
     * Ключ="значение" / "ключ":"значение" / escaped JSON. Открывающая кавычка
     * обязательна и входит в захват (сохраняется в выводе); значение может содержать
     * пробелы ({@code password="my secret passphrase"}). Escape-последовательности
     * без кавычки ({@code \n}, {@code \\}) поглощаются как содержимое; {@code \"}
     * считается закрывающим разделителем (см. ограничения в javadoc класса).
     */
    private static final Pattern QUOTED_SECRET_VALUE = Pattern.compile(
            "(?i)((?:" + SECRET_KEYS + ")" + OPTIONAL_QUOTE + "\\s*[:=]\\s*\\\\?[\"'])"
                    + "((?:\\\\[^\"'\\r\\n]|[^\"'\\r\\n\\\\])*)");

    /** Ключ=значение без кавычек: значение до whitespace/кавычки/разделителя/обратного слэша. */
    private static final Pattern UNQUOTED_SECRET_VALUE = Pattern.compile(
            "(?i)((?:" + SECRET_KEYS + ")" + OPTIONAL_QUOTE + "\\s*[:=]\\s*)(" + VALUE_CHAR + "+)");

    /**
     * Standalone Bearer/Basic без префикса Authorization (например, bare-значение
     * в MDC под {@code %X{key}}). Токеноподобное значение ≥8 символов
     * JWT/base64url-алфавита с опциональным base64-padding.
     */
    private static final Pattern STANDALONE_AUTH_SCHEME = Pattern.compile(
            "(?i)\\b(bearer|basic)[ \\t]+([A-Za-z0-9._~+/\\-]{8,}={0,2})");

    /** Credentials в URL: scheme://user:pass@host (postgresql, https, jdbc:postgresql и т.п.). */
    private static final Pattern URL_CREDENTIALS = Pattern.compile(
            "(?i)([a-z][a-z0-9+.-]*://[^/\\s:@]+:)([^@\\s/]+)(@)");

    /** Полный PEM-блок приватного ключа (multiline, ленивый матч до END), включая PGP BLOCK. */
    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "-----BEGIN [A-Z0-9 ]*PRIVATE KEY(?: BLOCK)?-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY(?: BLOCK)?-----");

    /** Усечённый блок без END (обрыв лога): fail-closed, маскируем всё после BEGIN. */
    private static final Pattern PRIVATE_KEY_UNTERMINATED = Pattern.compile(
            "-----BEGIN [A-Z0-9 ]*PRIVATE KEY(?: BLOCK)?-----[\\s\\S]*");

    private SecretMasker() {
    }

    /**
     * Маскирует секреты в тексте. Чистый текст не изменяется; операция идемпотентна.
     * {@code null}/пустая строка возвращаются как есть.
     */
    static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = BOT_TOKEN.matcher(text).replaceAll("bot" + REDACTED);
        // PEM-блоки раньше key=value: base64-тело не должно частично уцелеть.
        result = PRIVATE_KEY_BLOCK.matcher(result).replaceAll(REDACTED_PRIVATE_KEY);
        result = PRIVATE_KEY_UNTERMINATED.matcher(result).replaceAll(REDACTED_PRIVATE_KEY);
        result = AUTHORIZATION.matcher(result).replaceAll("$1" + REDACTED);
        // Standalone-схемы раньше key=value: «token=Bearer <jwt>» не должен оставить jwt в хвосте.
        result = STANDALONE_AUTH_SCHEME.matcher(result).replaceAll("$1 " + REDACTED);
        result = QUOTED_SECRET_VALUE.matcher(result).replaceAll("$1" + REDACTED);
        result = UNQUOTED_SECRET_VALUE.matcher(result).replaceAll("$1" + REDACTED);
        result = URL_CREDENTIALS.matcher(result).replaceAll("$1" + REDACTED + "$3");
        return result;
    }
}
