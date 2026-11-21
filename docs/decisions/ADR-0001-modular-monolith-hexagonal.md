# ADR-0001: Modular Monolith + Hexagonal Architecture

- **Статус**: Accepted (зафиксировано initSpec.md §3)
- **Дата**: 2026-11-20

## Context

SmartBot — публичный Telegram inline AI-бот с интеграциями (Telegram, GigaChat,
search, PostgreSQL). Команда небольшая; нужна скорость разработки без
операционной сложности микросервисов, но с возможностью замены LLM-провайдера
(provider-agnostic) и чёткими границами.

## Decision

- **Modular Monolith**: один deployable (smartbot-app), 10 Maven-модулей.
- **Hexagonal Architecture**: domain и application-api — чистое ядро без
  фреймворков; адаптеры реализуют порты; wiring только в composition root.
- Границы enforced автоматически: **ArchUnit** тесты в CI.
- Domain не зависит от Spring и внешних SDK; application-api не зависит от
  Telegram/GigaChat реализаций; адаптеры изолированы друг от друга.

## Consequences

- ✅.Provider-agnostic LLM: GigaChat заменяется без изменения ядра.
- ✅ Тестируемость: ядро — unit-тесты без Spring; адаптеры — WireMock/Testcontainers.
- ✅ Готовность к будущему выделению модулей в сервисы (границы уже есть).
- ⚠️ Больше boilerplate (маппинг DTO ↔ domain), дисциплина зависимостей.
- ⚠️ Единый процесс: масштабирование только вертикальное/репликами (приемлемо).
