package ru.smartbot.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.smartbot.adapter.telegram.model.TelegramUpdate;
import ru.smartbot.application.api.error.SafeErrorMessages;
import ru.smartbot.application.api.request.GenerateAnswerUseCase;
import ru.smartbot.application.api.request.HandleCallbackUseCase;
import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.domain.error.DomainException;
import ru.smartbot.domain.error.ErrorCode;

import java.util.Objects;

/**
 * Обработка callback_query (AD-8, AD-13, AD-23).
 *
 * <p>{@code answerCallbackQuery} отправляется немедленно при валидном callback
 * (до генерации), чтобы Telegram не показывал долгую загрузку. Ошибки —
 * безопасным текстом из единственного каталога (AD-20).
 */
@Component
public class CallbackQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(CallbackQueryHandler.class);

    private final HandleCallbackUseCase handleCallback;
    private final GenerateAnswerUseCase generateAnswer;
    private final TelegramApiClient api;

    public CallbackQueryHandler(HandleCallbackUseCase handleCallback,
                                GenerateAnswerUseCase generateAnswer,
                                TelegramApiClient api) {
        this.handleCallback = Objects.requireNonNull(handleCallback, "handleCallback");
        this.generateAnswer = Objects.requireNonNull(generateAnswer, "generateAnswer");
        this.api = Objects.requireNonNull(api, "api");
    }

    public void handle(TelegramUpdate update) {
        TelegramUpdate.CallbackQuery callback = update.callbackQuery();
        if (callback == null) {
            return;
        }
        try {
            HandleCallbackUseCase.Result result = handleCallback.handle(
                    new HandleCallbackUseCase.Command(update.updateId(),
                            principal(callback.from()), callback.data(), callback.inlineMessageId()));

            if (result.duplicate()) {
                log.debug("callback update_id={} уже обработан", update.updateId());
                return;
            }
            // Немедленный ack (AD-8): без текста, без alert
            api.answerCallbackQuery(callback.id(), null);

            // Асинхронная генерация (AD-8/15): один in-flight исполнитель на запрос
            if (result.requestId() != null) {
                generateAnswer.start(result.requestId());
            }
        } catch (DomainException e) {
            log.debug("callback update_id={} отклонён: {}", update.updateId(), e.getCode());
            api.answerCallbackQuery(callback.id(), SafeErrorMessages.messageFor(e.getCode()));
        } catch (RuntimeException e) {
            log.warn("Ошибка callback update_id={}: {}", update.updateId(), e.getClass().getSimpleName());
            api.answerCallbackQuery(callback.id(), SafeErrorMessages.messageFor(ErrorCode.INTERNAL));
        }
    }

    private static TelegramPrincipal principal(TelegramUpdate.User from) {
        return new TelegramPrincipal(from.id(), from.username(), from.firstName(), from.languageCode());
    }
}
