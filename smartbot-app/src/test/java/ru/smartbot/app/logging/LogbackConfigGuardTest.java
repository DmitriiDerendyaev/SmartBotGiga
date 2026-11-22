package ru.smartbot.app.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.smartbot.infrastructure.logging.MaskingMdcConverter;
import ru.smartbot.infrastructure.logging.MaskingThrowableProxyConverter;
import ru.smartbot.infrastructure.logging.SecretMaskingConverter;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config-guard (AD-4, Story 1.1): logback.xml и logback-spring.xml парсятся как XML,
 * и тест падает при сырых conversion words в appender patterns (%msg, %message, %ex,
 * %exception, %xEx, %xThrowable, %nopex и т.п.) — masking-конвертеры обязательны
 * во всех appender'ах. Дополнительно guard проверяет, что conversionRules masking-версий
 * (включая переопределение встроенных mdc/X) присутствуют в обоих конфигах.
 */
class LogbackConfigGuardTest {

    /**
     * Сырые conversion words logback, выводящие сообщение/stacktrace без masking.
     * Учитываются формат-модификаторы (%-5msg, %.30ex). Кастомные слова
     * (maskedMsg/maskedEx) не попадают: после % требуется точное совпадение слова
     * с границей (\b), а «masked…» не начинается с сырого слова.
     */
    private static final Pattern RAW_CONVERSION_WORD = Pattern.compile(
            "%(?:-?\\d+)?(?:\\.-?\\d+)?"
                    + "(msg|message|m|ex|exception|throwable|xEx|xException|xThrowable"
                    + "|nopex|nopexception|rEx|rootException)\\b");

    @ParameterizedTest
    @ValueSource(strings = {"/logback.xml", "/logback-spring.xml"})
    @DisplayName("Appender patterns не содержат сырых conversion words (%msg/%ex/…)")
    void appenderPatternsNeverUseRawConversionWords(String configResource) throws Exception {
        Document document = parse(configResource);
        NodeList patterns = document.getElementsByTagName("pattern");
        assertThat(patterns.getLength())
                .as("в %s должен быть хотя бы один appender pattern", configResource)
                .isPositive();

        List<String> violations = new ArrayList<>();
        for (int i = 0; i < patterns.getLength(); i++) {
            String pattern = patterns.item(i).getTextContent();
            Matcher raw = RAW_CONVERSION_WORD.matcher(pattern);
            while (raw.find()) {
                violations.add("%" + raw.group(1) + " в pattern: " + pattern);
            }
            // Каждый appender обязан маскировать и сообщение, и stacktrace (frozen «Always»).
            assertThat(pattern)
                    .as("pattern в %s обязан использовать %%maskedMsg и %%maskedEx", configResource)
                    .contains("%maskedMsg", "%maskedEx");
        }
        assertThat(violations)
                .as("сырые conversion words в %s запрещены (AD-4)", configResource)
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/logback.xml", "/logback-spring.xml"})
    @DisplayName("conversionRules masking-конвертеров (включая mdc/X) присутствуют в обоих конфигах")
    void maskingConversionRulesArePresent(String configResource) throws Exception {
        Document document = parse(configResource);
        NodeList rules = document.getElementsByTagName("conversionRule");
        Map<String, String> wordToConverter = new HashMap<>();
        for (int i = 0; i < rules.getLength(); i++) {
            Element rule = (Element) rules.item(i);
            wordToConverter.put(rule.getAttribute("conversionWord"), rule.getAttribute("class"));
        }

        assertThat(wordToConverter)
                .containsEntry("maskedMsg", SecretMaskingConverter.class.getName())
                .containsEntry("maskedEx", MaskingThrowableProxyConverter.class.getName())
                .containsEntry("mdc", MaskingMdcConverter.class.getName())
                .containsEntry("X", MaskingMdcConverter.class.getName());
    }

    private static Document parse(String configResource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Конфиги статические и свои; doctype/внешние сущности запрещены на всякий случай.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try (InputStream in = LogbackConfigGuardTest.class.getResourceAsStream(configResource)) {
            assertThat(in).as("ресурс %s существует", configResource).isNotNull();
            return factory.newDocumentBuilder().parse(in);
        }
    }
}
