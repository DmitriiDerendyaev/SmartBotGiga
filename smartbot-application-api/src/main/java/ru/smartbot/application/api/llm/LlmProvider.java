package ru.smartbot.application.api.llm;

import ru.smartbot.application.api.error.SafeErrorMessages;
import ru.smartbot.domain.error.ErrorCode;

import java.util.List;
import java.util.Objects;

/**
 * Порт LLM-провайдера (outbound, AD-6, AD-16, AD-21).
 *
 * <p>Контракт: синхронный {@link #complete}; ядро видит только порт, поэтому
 * GigaChat (Epic 2) заменяет mock без изменения application-impl. Usage
 * возвращается ядру (единственный писатель — impl, AD-16); адаптер ничего
 * не персистит. Streaming — не в initial release; function calling — в контракте.
 */
public interface LlmProvider {

    /** Код провайдера, например "mock" или "gigachat". */
    String code();

    LlmResponse complete(LlmRequest request);

    /**
     * @param prompt          текст запроса (данные, не инструкции — AD-25)
     * @param model           эффективная модель (иерархия config, AD-24)
     * @param maxOutputTokens единственный источник лимита (AD-18)
     * @param tools           allowlist инструментов (может быть пустым)
     */
    record LlmRequest(String prompt, String model, int maxOutputTokens, List<String> tools) {

        public LlmRequest {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(model, "model");
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    /**
     * @param text        сгенерированный текст (может содержать HTML-спецсимволы)
     * @param model       фактическая модель
     * @param usage       per-call usage (возвращается ядру)
     * @param latencyMs   длительность вызова (измеряет адаптер)
     */
    record LlmResponse(String text, String model, LlmUsage usage, long latencyMs) {

        public LlmResponse {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(usage, "usage");
        }
    }

    /** Per-call usage (AD-16): одна строка ai_request_usage на вызов. */
    record LlmUsage(int promptTokens, int completionTokens, int totalTokens) {
    }
}
