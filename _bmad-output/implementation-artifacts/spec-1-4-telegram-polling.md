---
title: 'Story 1.4 — Telegram polling и мгновенный лёгкий результат'
type: 'feature'
created: '2026-11-22'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'ccb4c5a'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

## Intent

**Problem:** Бот не получает updates из Telegram и не отвечает на inline_query (FR1, FR2, FR4, FR22; AD-8, AD-13, AD-23).

**Approach:** WebClient-клиент Bot API, DTO update/result, InlineQueryHandler (лёгкий article + кнопка Generate Answer, HTML-экранирование, безопасные ошибки из каталога), long polling со SmartLifecycle (at-least-once: offset после обработки, монотонное хранение в БД), всё выключено флагом до Epic 5.

## Boundaries & Constraints

**Always:** только через baseUrl (VPN-маршрут в prod); токен из env, не в логах; `is_personal=true`, `cache_time=0`; кнопка — на опубликованном сообщении; callback_data = opaque token из impl; ошибки — только `SafeErrorMessages`; offset монотонный, после персистентной обработки; один poller на token; HTML-экранирование query.

**Never:** парсинг/генерация token в адаптере; сырые тексты исключений пользователю; отправка ответа на дубль update; сетевые вызовы при старте без флага.

## Verification Results

- `make verify` → BUILD SUCCESS.
  - adapter-telegram: InlineQueryHandlerTest 4 (article+кнопка, HTML-escape, дубль, доменный отказ),
    TelegramApiClientTest 2 (WireMock: is_personal=true, cache_time=0, token в callback_data; offset в getUpdates);
  - persistence: PollingOffsetRepositoryAdapterIT 1 (монотонный offset), регрессия 6+6;
  - app smoke IT 1/1.

## Suggested Review Order

- Договор с Bot API: article + кнопка, parse_mode HTML
  [`TelegramInlineResult.java:24`](../../smartbot-adapter-telegram/src/main/java/ru/smartbot/adapter/telegram/model/TelegramInlineResult.java#L24)
- Handler: principal → use case → article; безопасные ошибки
  [`InlineQueryHandler.java:47`](../../smartbot-adapter-telegram/src/main/java/ru/smartbot/adapter/telegram/InlineQueryHandler.java#L47)
- Polling: offset после обработки, backoff, graceful stop
  [`TelegramPollingService.java:34`](../../smartbot-adapter-telegram/src/main/java/ru/smartbot/adapter/telegram/TelegramPollingService.java#L34)
- Клиент Bot API (baseUrl + token, без логирования URL)
  [`TelegramApiClient.java:28`](../../smartbot-adapter-telegram/src/main/java/ru/smartbot/adapter/telegram/TelegramApiClient.java#L28)
- Offset монотонно в БД
  [`V4__telegram_polling.sql:1`](../../smartbot-adapter-persistence/src/main/resources/db/migration/V4__telegram_polling.sql#L1)
