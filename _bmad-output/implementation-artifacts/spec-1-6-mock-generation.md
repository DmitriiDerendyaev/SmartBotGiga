---
title: 'Story 1.6 — Mock-генерация и обновление сообщения'
type: 'feature'
created: '2026-11-22'
status: 'done'
review_loop_iteration: 0
baseline_commit: '8d6105d'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

## Intent

**Problem:** После нажатия кнопки ничего не генерируется и сообщение не обновляется; нет контракта LLM, доставки и recovery (FR5–FR7, FR10–FR11; AD-6/8/15/16/18/19).

**Approach:** Порт `LlmProvider` + mock-провайдер, `InlineMessageEditor` (editMessageText), `GenerateAnswerService` (CAS CREATED→GENERATING + lease → LLM → CAS COMPLETED → edit), `FinalTextBuilder` (HTML-escape, обрезка тела, sources не теряются), recovery осиротевших GENERATING и watchdog TTL, виртуальные потоки.

## Verification Results

- `make verify` → BUILD SUCCESS; всего **172 теста** зелёные.
  - impl: FinalTextBuilderTest 3 (escape/truncate/sources), GenerateAnswerServiceTest 5
    (happy path, проигранный CAS не редактирует, delivery_error отдельно, FAILED при ошибке LLM, идемпотентный start);
  - adapter: TelegramInlineMessageEditorTest 3 (not modified/not found/failed), CallbackQueryHandlerTest 3 (+ запуск генерации);
  - ArchUnit-страж поймал реальное нарушение (getMessage в адаптере) — исправлено.

## Suggested Review Order

- Порт LLM: синхронный complete + usage + tools
  [`LlmProvider.java:20`](../../smartbot-application-api/src/main/java/ru/smartbot/application/api/llm/LlmProvider.java#L20)
- Генерация: CAS/lease/доставка/ошибки
  [`GenerateAnswerService.java:64`](../../smartbot-application-impl/src/main/java/ru/smartbot/application/impl/request/GenerateAnswerService.java#L64)
- Финальный текст: HTML + обрезка с сохранением sources
  [`FinalTextBuilder.java:24`](../../smartbot-application-impl/src/main/java/ru/smartbot/application/impl/request/FinalTextBuilder.java#L24)
- Recovery и watchdog
  [`RequestRecoveryService.java:36`](../../smartbot-application-impl/src/main/java/ru/smartbot/application/impl/request/RequestRecoveryService.java#L36)
- editMessageText: классификация ошибок
  [`TelegramInlineMessageEditor.java:33`](../../smartbot-adapter-telegram/src/main/java/ru/smartbot/adapter/telegram/TelegramInlineMessageEditor.java#L33)
