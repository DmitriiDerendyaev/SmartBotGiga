---
title: SmartBot — PRD (derived)
source: initSpec.md
status: final
created: 2026-11-20
note: >
  Сокращённая BMAD-цепочка по решению пользователя: initSpec.md уже содержит
  продуктовые решения и product brief. Данный PRD — формальная выжимка
  требований (FR/NFR) из initSpec.md для epic decomposition, без новых
  продуктовых решений.
---

# SmartBot — Product Requirements

## Product Summary

Публичный Telegram inline AI-ассистент. Вызов `@SmartBot <запрос>` из любого
чата без добавления бота. Ответ генерируется GigaChat (опционально с web
search), пользователь выбирает результат и публикует в текущий чат. Каждый
запрос независим, контекст диалога отключён. Деплой: российский VPS, Telegram
через Amnezia VPN, GigaChat напрямую.

## Functional Requirements

FR1: Бот принимает inline queries (`inline_query`) из любого чата; доступ публичный, добавление бота в чат не требуется.
FR2: Бот возвращает `InlineQueryResultArticle`; выбор результата пользователем публикует ответ в текущий чат.
FR3: Каждый запрос обрабатывается независимо; история диалога не используется и не хранится.
FR4: На inline query возвращается мгновенный лёгкий результат с действием **Generate Answer** (кнопка).
FR5: По callback: валидация → создание AiRequest → асинхронная генерация (search при необходимости → GigaChat) → обновление сообщения.
FR6: Опубликованное inline-сообщение обновляется через `editMessageText` по мере готовности ответа.
FR7: Отсутствующий `inline_message_id`, истёкшие и невалидные callback обрабатываются gracefully (безопасный ответ, без падений).
FR8: GigaChat OAuth: Authorization Key + scope, уникальный `RqUID` (UUID) на каждый OAuth-запрос, автоматический thread-safe refresh access token по expiration.
FR9: Модель GigaChat конфигурируема; список доступных моделей проверяется через API аккаунта; хардкод непроверенной модели запрещён.
FR10: Streaming и Function Calling используются там, где поддерживаются провайдером.
FR11: Token usage (prompt/completion/total) фиксируется для каждого запроса.
FR12: Режимы поиска: NONE, NATIVE, EXTERNAL, AUTO — конфигурируемы; за запрос не более одного необходимого провайдера.
FR13: Внешний поиск возвращает нормализованные результаты: title, url, snippet, publicationDate (если есть), sourceMetadata; исходные URL сохраняются в финальном ответе.
FR14: Function Calling может вызывать внешний search tool; сам по себе интернет-доступом не считается.
FR15: Автоматическая регистрация пользователя при первом обращении; идентификация по Telegram ID.
FR16: Per-user rate limiting и квоты запросов.
FR17: Global LLM budget, максимум output tokens, request timeouts.
FR18: Идемпотентная обработка updates; защита от дублей.
FR19: Персистентность сущностей: User, UserSettings, AiRequest, AiProvider, SearchProvider, AiRequestSearch, AiRequestUsage.
FR20: История запросов configurable; приватность пользователя соблюдается.
FR21: `chosen_inline_result` аналитика — опционально.
FR22: `answerCallbackQuery` и `answerInlineQuery` корректно вызываются; `is_personal=true` и адекватный cache_time для персонализированных AI-результатов.
FR23: Лимиты длины Telegram inline query/результатов соблюдаются.
FR24: Наружу — только безопасные сообщения об ошибках; чувствительные данные redact'ятся в логах; credentials AI-провайдера не раскрываются через Telegram.
FR25: LLM-слой provider-agnostic: GigaChat реализует общий интерфейс LlmProvider; замена провайдера не затрагивает ядро.

## Non-Functional Requirements

NFR1: Java 21, Spring Boot, Maven multi-module (10 модулей), Modular Monolith + Hexagonal Architecture; границы enforced (domain без Spring/SDK; application-api без Telegram/GigaChat реализаций).
NFR2: Весь Telegram-трафик — через существующую Amnezia VPN; GigaChat и прочие сервисы — напрямую; протокол VPN не предполагается до проверки; VPN-инфраструктура не меняется без approval.
NFR3: Long Polling предпочтителен для initial implementation; webhook — альтернатива после проверки inbound; одновременно не используются; решение фиксируется ADR.
NFR4: TLS-верификация не отключается в production; корневой сертификат Минцифры РФ в Java truststore; DNS/TLS/API проверяются до реализации.
NFR5: Секреты — только Infisical / защищённые env; никогда в Git, логах, workflow-логах; SSH-ключи не передаются через чат.
NFR6: PostgreSQL + Flyway; UUID внутренние id, BIGINT Telegram id; индексы, FK, unique constraints; конфигурация провайдеров отдельно от секретов.
NFR7: Docker / Docker Compose; образы в GHCR; GitHub Actions: feature/* → build+tests, develop → DEV, release → PROD; деплой воспроизводим и документирован; rollback plan; approval перед production-изменениями.
NFR8: Тесты: JUnit 5, Testcontainers, WireMock; каждая интеграция тестируется независимо.
NFR9: Мониторинг: Micrometer; Prometheus/Grafana когда обосновано.
NFR10: Все лимиты конфигурируемы, без хардкода коммерческих правил.
NFR11: Документация по списку §15 (README, PROJECT_BOOTSTRAP, architecture/*, database/*, api/contracts, ADR-*, runbooks/*); диаграммы Mermaid/PlantUML; актуальность поддерживается.
NFR12: Работа инкрементальная; инфраструктурные предположения не делаются без проверки; блокеры и решения документируются; в конце каждой фазы — отчёт (completed/changed/tests/infra/blockers/security/next).
NFR13: Redis — только при обосновании.
NFR14: Успех деплоя не заявляется без verification работающего приложения.

## Additional Requirements (Architecture)

- Architecture Spine: `_bmad-output/planning-artifacts/architecture/architecture-SmartBot-2026-11-20/ARCHITECTURE-SPINE.md` (AD-1…AD-12 — binding).
- Сборка через Docker (ADR-0003): `make build/test/verify`, локальный JDK не требуется.
- Spring Boot 4.2.0, ArchUnit 1.5.1, WireMock 3.13.2, Testcontainers 2.x (версии проверены 2026-11-20).
- Phase 0 gate: интеграции с реальными Telegram/GigaChat не верифицируются до infrastructure assessment; локальная реализация допускается с mock/WireMock.
- Документация на русском, англ. термины сохраняются.

## UX Design Requirements

Не применимо: UI — стандартный Telegram inline-интерфейс; кастомного UI нет.
Единственный UX-контракт: мгновенный лёгкий результат + кнопка Generate Answer +
обновление сообщения (FR4–FR7), безопасные сообщения об ошибках (FR24).
