/**
 * SmartBot Adapter LLM — адаптеры LLM-провайдеров.
 *
 * <p>Первый провайдер: GigaChatProvider (OAuth, RqUID, thread-safe token refresh,
 * chat completions, usage tracking, retry/timeouts) — Phase 4.
 * Базовый URL: https://api.giga.chat, endpoint POST /v1/chat/completions.
 *
 * <p>Границы модуля: реализует порт LlmProvider из application-api;
 * не зависит от других адаптеров и application-impl.
 */
package ru.smartbot.adapter.llm;
