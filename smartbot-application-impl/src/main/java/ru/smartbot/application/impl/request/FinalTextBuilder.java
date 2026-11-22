package ru.smartbot.application.impl.request;

import org.springframework.stereotype.Component;
import ru.smartbot.application.api.telegram.MessageLimits;

import java.util.List;
import java.util.Objects;

/**
 * Сборка финального текста для Telegram (AD-19).
 *
 * <p>Владелец форматирования — application-impl: экранирование HTML,
 * обрезка тела до 4096 символов, sources-блок с оригинальными URL **никогда**
 * не обрезается (AD-7 приоритетнее полноты ответа). Адаптер отправляет as-is.
 */
@Component
public class FinalTextBuilder {

    /**
     * @param modelText ответ LLM (может содержать HTML-спецсимволы)
     * @param sources   оригинальные URL (могут быть пустыми)
     * @return полный и отправляемый текст + флаг truncation
     */
    public BuiltText build(String modelText, List<String> sources) {
        Objects.requireNonNull(modelText, "modelText");
        String escaped = escapeHtml(modelText.strip());
        String sourcesBlock = sourcesBlock(sources == null ? List.of() : sources);

        String full = sourcesBlock.isEmpty() ? escaped : escaped + "\n\n" + sourcesBlock;
        String sent = truncateKeepingSources(escaped, sourcesBlock);
        return new BuiltText(full, sent, !full.equals(sent));
    }

    private static String sourcesBlock(List<String> sources) {
        if (sources.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<b>Источники:</b>\n");
        for (int i = 0; i < sources.size(); i++) {
            sb.append(i + 1).append(". ").append(escapeHtml(sources.get(i))).append('\n');
        }
        return sb.toString().strip();
    }

    private static String truncateKeepingSources(String escapedBody, String sourcesBlock) {
        int budget = MessageLimits.MAX_MESSAGE_LENGTH
                - (sourcesBlock.isEmpty() ? 0 : sourcesBlock.length() + 2);
        if (budget <= 0) {
            // Экстремальный случай: sources сами по себе не влезают — приоритет URL (AD-7/19)
            return sourcesBlock;
        }
        if (escapedBody.length() <= budget) {
            return sourcesBlock.isEmpty() ? escapedBody : escapedBody + "\n\n" + sourcesBlock;
        }
        String cut = escapedBody.substring(0, Math.max(0, budget - 1)) + "…";
        return sourcesBlock.isEmpty() ? cut : cut + "\n\n" + sourcesBlock;
    }

    /** Экранирование под parse_mode=HTML (без spring-web в application-impl, AD-2). */
    static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * @param fullText полный текст (до обрезки) — хранится в БД
     * @param sentText отправленная версия (≤4096) — хранится в БД
     * @param truncated true, если отправленная версия обрезана
     */
    public record BuiltText(String fullText, String sentText, boolean truncated) {
    }
}
