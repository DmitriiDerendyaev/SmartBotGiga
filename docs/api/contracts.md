# API Contracts — SmartBot

> Статус: зафиксировано Architecture Spine (AD-6, AD-13…AD-24).
> Контракты — binding для stories; изменения только через amendment spine.

## Внутренние порты (application-api)

### LlmProvider (outbound, AD-6/16/21)

```java
public interface LlmProvider {
    String code();                              // "gigachat"
    LlmCapabilities capabilities();             // functionCalling, streaming=false (initial), ...
    LlmResponse complete(LlmRequest request);   // синхронный; transport-retry внутри адаптера
}
// LlmRequest: messages, model, maxOutputTokens (единственный источник лимита, AD-18),
//             tools? (allowlist, AD-25), timeout
// LlmResponse: text, toolCalls, finishReason, usage (per-call), latencyMs
// Адаптер НИЧЕГО не персистит; usage возвращает ядру (AD-16).
// Streaming — не в initial release (Deferred).
```

### SearchProvider (outbound, AD-7)

```java
public interface SearchProvider {
    String code();
    List<SearchResult> search(SearchQuery query);
}
// SearchResult (domain): title: String, url: String (нормализованный, без tracking),
//   snippet: String (cap), publicationDate: Instant?, sourceMetadata: Map<String,String>
// Нормализация — в адаптере; валидация/дедупликация — в impl.
```

### InlineMessageEditor (outbound, AD-8/23)

```java
public interface InlineMessageEditor {
    EditResult edit(String inlineMessageId, String finalText); // HTML, as-is от impl
}
// transport-retry (429 retry_after) внутри адаптера;
// "message is not modified" = успех; "not found" → ErrorCode.EXPIRED/DELIVERY_FAILED
```

### Порты лимитов (outbound, AD-18; реализация — infrastructure)

`RateLimitPort`, `QuotaPort`, `BudgetPort` — вызываются **только из
application-impl** до перевода AiRequest в GENERATING; атомарный
reserve/settle/release budget. Дефолт — in-memory; Redis — ADR-gate.

### MessageLimits (AD-19)

Константы/порт adapter-telegram: 4096 символов сообщения, лимиты длины
inline query и callback_data (≤64 байт). Impl использует при формировании
финального текста; sources-блок не обрезается никогда.

### Репозитории (outbound, Phase 3)

`UserRepository`, `UserSettingsRepository`, `AiRequestRepository`,
`AiProviderRepository`, `SearchProviderRepository`, `AiRequestUsageRepository`,
`ProcessedUpdateRepository` (ledger, AD-14).

### Use cases (inbound, вызывает adapter-telegram)

- `HandleInlineQueryUseCase(query, TelegramPrincipal)` → создаёт AiRequest
  CREATED + opaque token (AD-13); возвращает лёгкий результат.
- `HandleCallbackUseCase(token, inlineMessageId, TelegramPrincipal)` —
  контракт фиксируется **до Phase 2**; валидация владельца/TTL/статуса (AD-13).
- `RegisterUser` — get-or-create внутри use cases (AD-17), отдельный вызов
  из адаптера не нужен.
- `GenerateAnswerUseCase` (async, Phase 6), `ExpireRequestUseCase` (AD-15),
  `UpdateUserSettingsUseCase`, `/privacy`, `/delete_me` (AD-26).

`TelegramPrincipal`: telegram_user_id (BIGINT), username?, first_name?, language_code?.

## AiRequest state machine (AD-15)

```
CREATED → GENERATING | EXPIRED | FAILED
GENERATING → COMPLETED | FAILED | EXPIRED
COMPLETED / FAILED / EXPIRED — терминальные, immutable
```

Переход — атомарный CAS по текущему статусу; lease/heartbeat для in-flight;
recovery при старте: осиротевшие GENERATING → FAILED `RESTARTED`.
Ошибка доставки после успешной генерации = COMPLETED + `delivery_error_code`.

## ErrorCode catalog (AD-20, enum в domain)

`VALIDATION, INVALID_CALLBACK, EXPIRED, BLOCKED, RATE_LIMITED, QUOTA_EXCEEDED,
BUDGET_EXCEEDED, TIMEOUT, PROVIDER_UNAVAILABLE, AUTH, SEARCH_UNAVAILABLE,
DELIVERY_FAILED, MESSAGE_TOO_LONG, RESTARTED, INTERNAL`

- Доменные исключения: только ErrorCode + типизированные безопасные параметры.
- Сырые внешние тела/заголовки не пересекают порт.
- Единственная таблица `ErrorCode → безопасный текст` (api/impl);
  adapter-telegram никогда не рендерит `getMessage()`.

## Внешние API

| API | Контракт |
|---|---|
| Telegram Bot API | https://core.telegram.org/bots/api (inline mode); at-least-once, ledger (AD-14/23) |
| GigaChat REST | https://api.giga.chat, POST /v1/chat/completions; OAuth ngw.devices.sberbank.ru:9443, RqUID UUID |
| External Search | TBD (Phase F); форма результатов уже зафиксирована (AD-7) |

## Конфигурация запроса (AD-24)

Эффективные значения вычисляет только impl:
`UserSettings → provider config (БД) → smartbot.* defaults`.
`parse_mode=HTML` на весь проект; финальный текст собирает impl (AD-19).
