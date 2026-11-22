package ru.smartbot.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import ru.smartbot.adapter.telegram.model.TelegramInlineResult;
import ru.smartbot.adapter.telegram.model.TelegramUpdate;
import ru.smartbot.application.api.error.SafeErrorMessages;
import ru.smartbot.application.api.request.HandleInlineQueryUseCase;
import ru.smartbot.application.api.telegram.MessageLimits;
import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.domain.error.DomainException;
import ru.smartbot.domain.error.ErrorCode;

import java.util.List;
import java.util.Objects;

/**
 * Обработка inline_query (AD-8, AD-13, AD-19): мгновенный лёгкий результат
 * с кнопкой Generate Answer; кнопка живёт на опубликованном сообщении.
 *
 * <p>Адаптер не парсит и не генерирует correlation token — только кладёт его
 * в callback_data. Текст ошибок берётся из единственной таблицы
 * {@link SafeErrorMessages} (AD-20), а не из исключений.
 */
@Component
public class InlineQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(InlineQueryHandler.class);

    static final String BUTTON_TEXT = "Сгенерировать ответ";
    private static final String TITLE = "SmartBot";
    private static final String PROMPT_TEXT =
            "<b>SmartBot</b>\n\n%s\n\nНажмите «Сгенерировать ответ».";

    private final HandleInlineQueryUseCase handleInlineQuery;
    private final TelegramApiClient api;

    public InlineQueryHandler(HandleInlineQueryUseCase handleInlineQuery, TelegramApiClient api) {
        this.handleInlineQuery = Objects.requireNonNull(handleInlineQuery, "handleInlineQuery");
        this.api = Objects.requireNonNull(api, "api");
    }

    public void handle(TelegramUpdate update) {
        TelegramUpdate.InlineQuery inline = update.inlineQuery();
        if (inline == null) {
            return;
        }
        try {
            HandleInlineQueryUseCase.Result result = handleInlineQuery.handle(
                    new HandleInlineQueryUseCase.Command(update.updateId(),
                            principal(inline.from()), inline.query()));

            if (result.duplicate()) {
                log.debug("inline_query update_id={} уже обработан — повторный ответ не отправляется",
                        update.updateId());
                return;
            }

            String safeQuery = HtmlUtils.htmlEscape(inline.query() == null ? "" : inline.query().strip());
            String text = PROMPT_TEXT.formatted(safeQuery);
            String description = truncate(inline.query(), 100);

            api.answerInlineQuery(inline.id(), List.of(
                    TelegramInlineResult.article(
                            "answer-" + result.requestId(),
                            TITLE,
                            description,
                            text,
                            BUTTON_TEXT,
                            result.correlationToken())));
        } catch (DomainException e) {
            log.debug("inline_query update_id={} отклонён: {}", update.updateId(), e.getCode());
            api.answerInlineQuery(inline.id(), List.of(
                    TelegramInlineResult.article(
                            "error-" + update.updateId(),
                            TITLE,
                            null,
                            HtmlUtils.htmlEscape(SafeErrorMessages.messageFor(e.getCode())),
                            null,
                            null)));
        } catch (RuntimeException e) {
            log.warn("Ошибка обработки inline_query update_id={}: {}", update.updateId(), e.getClass().getSimpleName());
            api.answerInlineQuery(inline.id(), List.of(
                    TelegramInlineResult.article(
                            "error-" + update.updateId(),
                            TITLE,
                            null,
                            HtmlUtils.htmlEscape(SafeErrorMessages.messageFor(ErrorCode.INTERNAL)),
                            null,
                            null)));
        }
    }

    private static TelegramPrincipal principal(TelegramUpdate.User from) {
        return new TelegramPrincipal(from.id(), from.username(), from.firstName(), from.languageCode());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max - 1) + "…";
    }
}
