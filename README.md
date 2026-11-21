# SmartBot

Публичный Telegram **inline AI-ассистент** на базе **GigaChat**.

Вызов из любого чата без добавления бота в conversation:

```
@SmartBot Explain how Kafka works
```

Бот получает inline query → (опционально) web search → GigaChat → пользователь
выбирает результат и публикует его в текущий чат. Контекст диалога не хранится:
каждый запрос независим.

## Технологический стек

| Компонент | Технология |
|---|---|
| Язык | Java 21 |
| Framework | Spring Boot 4.2 |
| Архитектура | Modular Monolith + Hexagonal Architecture |
| Сборка | Maven multi-module (10 модулей) |
| БД | PostgreSQL 17 + Flyway |
| Cache | Redis (опционально, profile `cache`) |
| LLM | GigaChat API (provider-agnostic слой `LlmProvider`) |
| Контейнеризация | Docker / Docker Compose |
| CI/CD | GitHub Actions → GHCR |
| Секреты | Infisical |
| Тесты | JUnit 5, ArchUnit, Testcontainers, WireMock |
| Метрики | Micrometer + Prometheus |

## Модули

```
smartbot-app                  ← composition root (Spring Boot main, wiring)
├── smartbot-infrastructure   ← конфигурация, метрики, rate limits, budget
├── smartbot-adapter-telegram ← Telegram Bot API (Inline Mode, VPN route)
├── smartbot-adapter-llm      ← GigaChatProvider (OAuth, completions)
├── smartbot-adapter-search   ← внешние SearchProvider
├── smartbot-adapter-persistence ← PostgreSQL, JPA, Flyway
└── smartbot-application-impl ← use cases
    └── smartbot-application-api ← порты (ports), контракты
        └── smartbot-domain   ← доменная модель (без Spring!)
            └── smartbot-common ← утилиты (чистая Java)
```

Границы enforced через **ArchUnit** (`smartbot-app/src/test/.../ArchitectureBoundariesTest.java`):
domain и application-api не зависят от Spring; адаптеры изолированы друг от друга;
только `smartbot-app` видит все слои. Подробнее: [docs/architecture/components.md](docs/architecture/components.md).

## Быстрый старт (всё через Docker, локальный JDK не нужен)

```bash
make up-dev      # PostgreSQL (+ Redis: docker compose -f docker-compose.dev.yml --profile cache up -d)
make test        # unit-тесты + ArchUnit в Maven-контейнере
make verify      # + integration tests (Testcontainers)
make image       # Docker-образ приложения
make up          # полный стек (требует .env, см. .env.example)
```

Произвольная Maven-команда: `make shell-mvn CMD="mvn dependency:tree"`.

## Сетевые ограничения (важно!)

Приложение деплоится на **российский VPS**, где прямой доступ к Telegram Bot API
заблокирован. Telegram-трафик идёт через существующую **Amnezia VPN**
инфраструктуру; GigaChat — напрямую. Маршрутизация определяется в Phase 0
и документируется в [docs/architecture/network-routing.md](docs/architecture/network-routing.md).

## Документация

- [PROJECT_BOOTSTRAP.md](PROJECT_BOOTSTRAP.md) — статус бутстрапа и фазы
- [docs/architecture/](docs/architecture/) — архитектура, сеть, интеграции
- [docs/decisions/](docs/decisions/) — ADR
- [initSpec.md](initSpec.md) — исходная спецификация (source of truth)

## Branching strategy

- `feature/*` — build + tests
- `develop` — deploy DEV
- `release` — deploy PROD

## Безопасность

Секреты (bot token, GigaChat Authorization Key, VPN keys) **никогда** не
коммитятся: только Infisical / защищённые env-переменные. См. `.gitignore`.
