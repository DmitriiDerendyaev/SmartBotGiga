package ru.smartbot.application.api.request;

import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.domain.request.AiRequestStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * Входящий контракт обработки callback_query (AD-8, AD-13, AD-14).
 *
 * <p>Валидация: запись существует, принадлежит {@code callback.from.id},
 * не истекла (TTL), статус допускает генерацию. Callback без
 * {@code inline_message_id} — валидационная ошибка (INVALID_CALLBACK),
 * не нормальный путь. EXPIRED ставит только application-impl.
 *
 * <p>Повторный callback по тому же сообщению идемпотентно возвращает
 * существующий запрос (одна публикация = один расход).
 */
public interface HandleCallbackUseCase {

    Result handle(Command command);

    /**
     * @param telegramUpdateId  id callback-update (ledger, AD-14)
     * @param principal         кто нажал
     * @param correlationToken  opaque token из callback_data (не парсится адаптером)
     * @param inlineMessageId   опубликованное сообщение; пусто — INVALID_CALLBACK
     */
    record Command(long telegramUpdateId, TelegramPrincipal principal,
                   String correlationToken, String inlineMessageId) {

        public Command {
            Objects.requireNonNull(principal, "principal");
        }
    }

    /**
     * @param requestId  существующий/принятый запрос
     * @param status     текущий статус после валидации
     * @param duplicate  update уже обработан (adapter не ack'ает повторно)
     * @param reusable   повторное нажатие по уже активному запросу — новая генерация не нужна
     */
    record Result(UUID requestId, AiRequestStatus status, boolean duplicate, boolean reusable) {
    }
}
