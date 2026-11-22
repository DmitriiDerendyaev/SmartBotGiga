package ru.smartbot.application.api.request;

import java.util.UUID;

/**
 * Входящий контракт асинхронной генерации ответа (AD-8, AD-15, AD-21).
 *
 * <p>Запускается после валидного callback. Реализация: CAS CREATED→GENERATING
 * с lease, вызов LlmProvider, сборка финального текста, editMessageText,
 * CAS GENERATING→COMPLETED. Один AiRequest = один in-flight исполнитель.
 */
public interface GenerateAnswerUseCase {

    /**
     * Запустить генерацию (идемпотентно): повторный вызов по активному запросу
     * не создаёт второй исполнитель.
     *
     * @param requestId существующий AiRequest
     * @return true — генерация запущена/уже идёт; false — статус не допускает
     */
    boolean start(UUID requestId);
}
