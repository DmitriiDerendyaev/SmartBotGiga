# System Context — SmartBot

> Статус: черновик Phase 1. Уточняется после Phase 0 (infrastructure assessment).

## Контекстная диаграмма

```mermaid
graph TB
    User["👤 Пользователь Telegram"]
    TG["Telegram Bot API<br/>(api.telegram.org)"]
    VPN["Amnezia VPN<br/>(существующая инфраструктура)"]
    App["SmartBot<br/>(Java 21 / Spring Boot,<br/>российский VPS)"]
    GC["GigaChat API<br/>(api.giga.chat)"]
    Search["External SearchProvider<br/>(не выбран, Phase 5)"]
    DB[("PostgreSQL")]
    Redis[("Redis<br/>(опционально)")]
    Infisical["Infisical<br/>(секреты)"]
    GHCR["GHCR + GitHub Actions"]

    User -->|"@SmartBot query"| TG
    TG -.->|"getUpdates / webhook<br/>❗ только через VPN"| VPN
    VPN --> App
    App -->|"answerInlineQuery,<br/>editMessageText<br/>❗ только через VPN"| VPN
    VPN -.-> TG
    App -->|"прямое соединение<br/>(DNS/TLS/Минцифры cert)"| GC
    App -->|"опционально"| Search
    App --> DB
    App -.-> Redis
    Infisical -.->|"runtime secrets"| App
    GHCR -.->|"deploy"| App
```

## Ключевые потоки

1. **Inline query**: пользователь набирает `@SmartBot <запрос>` в любом чате →
   Telegram присылает `inline_query` → SmartBot возвращает
   `InlineQueryResultArticle` (лёгкий результат с кнопкой **Generate Answer**).
2. **Генерация**: пользователь выбирает результат → `chosen_inline_result` /
   `callback_query` → асинхронная генерация (search → GigaChat) →
   `editMessageText` обновляет опубликованное inline-сообщение.
3. Контекст диалога **не используется**: каждый запрос независим.

## Внешние системы и ограничения

| Система | Направление | Сетевой маршрут | Статус проверки |
|---|---|---|---|
| Telegram Bot API | in/out | **только через Amnezia VPN** (прямой доступ с РФ VPS заблокирован) | ⏳ Phase 0 |
| GigaChat API | out | напрямую с РФ VPS | ⏳ Phase 0 (DNS, TLS, root cert Минцифры) |
| External Search | out | не определён (вероятно напрямую) | ⏳ Phase 5 |
| PostgreSQL | local | docker network / localhost | ✅ локально |
| Infisical | out | напрямую | ⏳ Phase 7 |

## Акторы

- **Пользователь Telegram** — публичный доступ, автоматическая регистрация при
  первом обращении, per-user rate limits и квоты.
- **Оператор (владелец)** — деплой, мониторинг, управление лимитами и бюджетом LLM.
