package ru.smartbot.application.api.request;

import ru.smartbot.application.api.user.TelegramPrincipal;

import java.util.Objects;

/**
 * Входящий контракт обработки inline_query (AD-13, AD-14, AD-19).
 *
 * <p>На inline_query impl создаёт персистентную корреляцию (AiRequest CREATED)
 * и возвращает opaque token, который adapter кладёт в callback_data и не
 * парсит. Повторный update гасится ledger'ом; повторный query в окне TTL
 * переиспользует существующую запись.
 *
 * <p>Контракт фиксируется до Phase 2: mock-реализация (story 1.6) и реальная
 * генерация (Epic 2) используют его без изменений.
 */
public interface HandleInlineQueryUseCase {

    /**
     * @param command валидированный вход адаптера
     * @return результат: token для callback_data и признак дубля/reuse
     */
    Result handle(Command command);

    /**
     * @param telegramUpdateId BIGINT update_id (ledger, AD-14)
     * @param principal        идентификация Telegram (AD-17)
     * @param queryText        текст запроса как есть (без обрезки, AD-19)
     */
    record Command(long telegramUpdateId, TelegramPrincipal principal, String queryText) {

        public Command {
            Objects.requireNonNull(principal, "principal");
        }
    }

    /**
     * @param requestId       UUID созданного/переиспользованного AiRequest
     * @param correlationToken opaque token ≤64 байт для callback_data
     * @param duplicate       update уже обработан ранее: adapter не отвечает повторно
     * @param reused          запись переиспользована в окне TTL (новая не создавалась)
     */
    record Result(java.util.UUID requestId, String correlationToken, boolean duplicate, boolean reused) {

        public static Result alreadyProcessed() {
            return new Result(null, null, true, false);
        }
    }
}
