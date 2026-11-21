# GigaChat Integration — SmartBot

> Статус: контракт Phase 1. Реализация — Phase 4. Credentials — только через Infisical.

## API

- Base URL: `https://api.giga.chat`
- Chat completions: `POST /v1/chat/completions`
- OAuth: документированный token endpoint + scope (`GIGACHAT_API_PERS` / `B2B` / `CORP`).
- Каждый OAuth-запрос содержит уникальный `RqUID` (UUID).
- Access token истекает → автоматический **thread-safe refresh** по expiration
  из ответа. Authorization Key — постоянный credential; access token никогда
  не запрашивается у пользователя как постоянный.
- Список моделей проверяется через API для конкретного аккаунта; хардкод
  непроверенной модели запрещён.

## TLS

- Прямое соединение с российского VPS (без VPN).
- Корневой сертификат **Минцифры РФ** должен быть в Java truststore.
- Отключение проверки TLS-сертификатов в production **запрещено**.
- Phase 0: проверить DNS, TLS handshake, сертификат.

## Компоненты (adapter-llm)

- `GigaChatProvider` implements `LlmProvider` (порт application-api).
- OAuth/token lifecycle manager.
- Model configuration (config, не хардкод).
- Chat completions; streaming и function calling — где поддерживается.
- Token usage tracking → `AiRequestUsage`.
- Error mapping, timeouts, retry policies.

## SDK vs REST

Перед кастомной REST-интеграцией оценить официальный GigaChat Java SDK
(https://developers.sber.ru/docs/ru/gigachain/tools/java/gigachat).
Критерии: зрелость, контроль таймаутов/retry, совместимость с WebClient-стеком,
лицензия. Решение → ADR (Phase 4).

## Function Calling и поиск

- Function Calling может использоваться для вызова внешнего search tool.
- Function Calling **сам по себе не даёт доступ в интернет**.
- Режимы поиска: NONE / NATIVE / EXTERNAL / AUTO (см. adapter-search).

## Открытые вопросы (Phase E)

- [ ] Authorization Key (Infisical)
- [ ] Scope аккаунта
- [ ] Доступные модели (проверить через API)
- [ ] Тарифные лимиты → global LLM budget

## Уточнено Architecture Spine (binding)

- **AD-6:** порт `LlmProvider` — синхронный `complete()` + tools; streaming
  не в initial release; usage **возвращается** ядру, адаптер не персистит
  (AD-16, per-call записи `(request_id, call_index)`).
- **AD-6/SEC-09:** корень Минцифры — в **scoped truststore** только для
  GigaChat-клиента (не глобальный cacerts); SHA-256 fingerprint сверен с
  официальным и зафиксирован здесь; проверить ОБА endpoint'а: OAuth
  `ngw.devices.sberbank.ru:9443` и API `api.giga.chat`; trust-all запрещён
  (CI grep + review).
- **AD-21:** single-flight token refresh (один refresh в полёт), safety
  margin configurable; transport-retry только внутри `complete()`;
  RqUUID — новый UUID на каждый OAuth-запрос.
- **AD-24:** проверенный список моделей — в `ai_providers.config`;
  `default_model` обязан входить в список; disabled provider →
  FAILED PROVIDER_UNAVAILABLE.
- **AD-18:** `maxOutputTokens` приходит в LlmRequest от impl — единственный
  источник лимита; provider передаёт его в API.
- Live probe 2026-11-21: `api.giga.chat` маршрут `/v1/chat/completions`
  подтверждён (401 без auth); существует `/v2` вариант для parallel tool
  calls (GigaChat-3-Ultra) — информационно, не используется в initial release.
