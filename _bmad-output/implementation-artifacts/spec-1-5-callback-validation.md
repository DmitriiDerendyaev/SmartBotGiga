---
title: 'Story 1.5 — Приём и валидация callback'
type: 'feature'
created: '2026-11-22'
status: 'done'
review_loop_iteration: 0
baseline_commit: '126d7ab'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

## Intent

**Problem:** Кнопка Generate Answer не защищена: нужна валидация владельца/TTL/статуса и идемпотентность, иначе чужие/поддельные/повторные нажатия запускают платную генерацию (FR7, FR18; AD-13/14/15/17).

**Approach:** Контракт `HandleCallbackUseCase` + сервис: ledger → регистрация → blocked → inline_message_id обязателен → поиск по opaque token → владелец → привязка сообщения (partial unique) → TTL→EXPIRED (CAS) → статус. Адаптер немедленно отправляет `answerCallbackQuery`.

## Verification Results

- `make verify` → BUILD SUCCESS.
  - impl: HandleCallbackServiceTest 8 (валидный, дубль, blocked, без inline_message_id, неизвестный token, чужой владелец, TTL→EXPIRED+CAS, конфликт сообщения);
  - adapter: CallbackQueryHandlerTest 3 (немедленный ack, без ack на дубль, безопасный alert);
  - persistence IT 7 (+attach-конфликт partial unique); app smoke IT 1/1.

## Suggested Review Order

- Валидатор callback: порядок проверок и CAS EXPIRED
  [`HandleCallbackService.java:62`](../../smartbot-application-impl/src/main/java/ru/smartbot/application/impl/request/HandleCallbackService.java#L62)
- Немедленный ack + безопасные тексты
  [`CallbackQueryHandler.java:41`](../../smartbot-adapter-telegram/src/main/java/ru/smartbot/adapter/telegram/CallbackQueryHandler.java#L41)
- Привязка сообщения в REQUIRES_NEW (partial unique не ломает внешнюю tx)
  [`AiRequestRepositoryAdapter.java:77`](../../smartbot-adapter-persistence/src/main/java/ru/smartbot/adapter/persistence/request/AiRequestRepositoryAdapter.java#L77)
