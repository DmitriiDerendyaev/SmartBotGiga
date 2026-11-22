package ru.smartbot.application.impl.llm;

import org.springframework.stereotype.Component;
import ru.smartbot.application.api.llm.LlmProvider;

import java.util.Objects;

/**
 * Mock LLM для Epic 1 (локальная разработка до подключения GigaChat).
 *
 * <p>Возвращает детерминированный ответ, демонстрирующий полный pipeline
 * (CAS, lease, сборка текста, editMessageText) без внешних вызовов.
 * Заменяется GigaChatProvider в Epic 2 через тот же порт (AD-6).
 */
@Component
public class MockLlmProvider implements LlmProvider {

    @Override
    public String code() {
        return "mock";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        long started = System.nanoTime();
        String answer = "Это тестовый ответ SmartBot (mock-провайдер).\n\n"
                + "Запрос: " + request.prompt();
        int promptTokens = request.prompt().length() / 4;
        int completionTokens = answer.length() / 4;
        return new LlmResponse(answer, request.model(),
                new LlmUsage(promptTokens, completionTokens, promptTokens + completionTokens),
                (System.nanoTime() - started) / 1_000_000);
    }
}
