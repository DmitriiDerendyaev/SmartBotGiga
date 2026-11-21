# Runbook: Troubleshooting — SmartBot

> Статус: заготовка Phase 1. Пополняется по мере реальных инцидентов.

## Диагностика по симптомам

### Бот не отвечает на inline query
1. `docker compose logs app` — ошибки polling/webhook?
2. VPN-канал: `curl --interface <vpn> https://api.telegram.org/bot<token>/getMe`
   (токен не логировать!).
3. Проверить, не установлен ли webhook при активном polling (конфликт).
4. Inline Mode включён в BotFather?

### GigaChat: TLS/сертификатные ошибки
- `SSLHandshakeException` → проверить корневой сертификат Минцифры в truststore:
  `keytool -list -cacerts | grep -i russian`
- НЕ отключать проверку TLS; исправить truststore.

### GigaChat: 401/403
- Authorization Key валиден? Scope соответствует аккаунту?
- Access token истёк → проверить token refresh (логи без секретов).
- Проверить RqUID уникальность в OAuth-запросах.

### Дубли ответов / повторная генерация
- Проверить идемпотентность по `update_id` (unique constraint).
- Callback expired/invalid → graceful ответ, не падать.

### editMessageText не обновляет сообщение
- `inline_message_id` отсутствует/истёк → обработать gracefully.
- Сообщение старше допустимого окна редактирования.

### Высокая latency / rate limits
- Метрики: `/actuator/prometheus` (latency, token usage, rate limit hits).
- Проверить per-user quotas и global LLM budget.

## Правила

- Секреты и персональные данные — redaction в логах.
- Любые изменения инфраструктуры — только с approval.
- Инциденты и решения фиксировать здесь.
