# ADR-0002: Telegram connectivity — Long Polling vs Webhook

- **Статус**: Proposed (ожидает Phase 0 — проверку VPN-канала)
- **Дата**: 2026-11-20

## Context

Российский VPS не имеет прямого доступа к Telegram Bot API. Трафик идёт через
Amnezia VPN (протокол и схема маршрутизации будут установлены в Phase 0).
Нужно выбрать способ получения updates. Одновременно `getUpdates` и webhook
для одного бота невозможны.

## Options

### A. Long Polling (`getUpdates`) через VPN
- Только исходящие соединения; не нужен публичный HTTPS/inbound.
- Проще firewall/TLS; естественно ложится на VPN-маршрут.
- Минусы: постоянные исходящие запросы, задержка до ~timeout polling,
  один активный poller на бота (при репликах нужен leader/очередь).

### B. Webhook
- Telegram сам доставляет updates; меньше исходящего трафика.
- Требует: публичный домен, валидный TLS, inbound-доступность с серверов
  Telegram до VPS (в РФ может быть нестабильно), `secret_token` validation.
- Входящий трафик НЕ требует VPN, но ответы (`answerInlineQuery` и др.) всё
  равно идут через VPN.

## Decision (предварительное)

**Long Polling** для initial implementation — если Phase 0 подтвердит
надёжность исходящего VPN-канала (initSpec.md §4.3 прямо рекомендует prefer
polling). Webhook рассматривается как fallback/эволюция.

## Consequences

- Один poller-инстанс; горизонтальное масштабирование app потребует
 leader election или выделенного update-receiver (учесть в Phase 6/7).
- Идемпотентность по `update_id` обязательна (возможны дубли при reconnect).
- Финальный статус ADR меняется после Phase 0 connectivity report.

## Обязательные контроли (Architecture Spine AD-23, security review SEC-04)

Независимо от выбора транспорта:

- **Long Polling:** webhook-endpoint не поднимается; offset персистится;
  at-least-once + ledger `processed_updates`; один poller на bot token
  во всех окружениях (DEV/PROD — разные боты, AD-11).
- **Webhook (если выбран):**
  1. `setWebhook` с `secret_token` (случайный, 1–256 символов, из Infisical);
     проверка заголовка `X-Telegram-Bot-Api-Secret-Token` (constant-time
     compare); несовпадение → 401/403 без тела.
  2. HTTPS с валидным сертификатом (порты 443/80/88/8443); терминация TLS —
     reverse proxy, наружу только webhook-путь.
  3. IP allowlist подсетей Telegram (149.154.160.0/20, 91.108.4.0/22) —
     defense-in-depth.
  4. Путь — случайный slug; **запрещён** путь вида `/bot<TOKEN>/webhook`
     (токен в access-логах proxy).
  5. Переключение polling↔webhook — явный `deleteWebhook`/`setWebhook`
     в runbook (одновременная работа невозможна).
