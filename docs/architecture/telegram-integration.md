# Telegram Integration — SmartBot

> Статус: контракт Phase 1. Реализация — Phase 2 (после проверки VPN-маршрута).

## Режим

- **Inline Mode**, публичный бот. Вызов `@SmartBot <query>` из любого чата.
- Контекст диалога не используется; каждый запрос независим.
- Транспорт: Long Polling (предварительно) или Webhook — по ADR-0002 после Phase 0.
- Весь исходящий трафик к Bot API — через утверждённый VPN-маршрут.

## Обрабатываемые updates

| Update | Обработка |
|---|---|
| `inline_query` | валидация длины query → лёгкий `InlineQueryResultArticle` с кнопкой **Generate Answer** (`is_personal=true`, cache_time минимальный) |
| `callback_query` | валидация callback → создание AiRequest → асинхронная генерация → `editMessageText` по `inline_message_id` |
| `chosen_inline_result` | аналитика выбора результата (опционально) |

## Методы Bot API

- `getUpdates` (если Long Polling)
- `answerInlineQuery`
- `answerCallbackQuery`
- `editMessageText` (обновление опубликованного inline-сообщения)

## Асинхронная генерация (Phase 6)

1. Лёгкий результат публикуется мгновенно (кнопка Generate Answer).
2. По callback: валидация → AiRequest → search (если нужен) → GigaChat →
   `editMessageText` → запись usage-метрик.
3. Graceful handling: отсутствующий `inline_message_id`, истёкшие/невалидные
   callback, timeout генерации.

## Ограничения и безопасность

- Лимиты длины inline query и результата Telegram — соблюдать.
- `is_personal=true` + корректный `cache_time` для персонализированных AI-ответов.
- Идемпотентная обработка updates (защита от дублей, `update_id`).
- Токен бота — только Infisical/env; никогда в Git, логах или ответах пользователю.
- Webhook (если выбран): обязательный `secret_token` validation.

## Открытые вопросы (Phase 0/2)

- [ ] Username бота, подтверждение Inline Mode в BotFather
- [ ] VPN-маршрут проверен end-to-end
- [ ] Polling vs webhook (ADR-0002)
- [ ] Inline feedback configuration нужен?

## Уточнено Architecture Spine (binding)

- **AD-13:** корреляция — персистентный AiRequest CREATED на inline_query;
  `callback_data` = opaque server-issued token (≤64 байт), adapter не парсит;
  query хранится только в БД (TTL); кнопка — только на опубликованном сообщении;
  callback без `inline_message_id` = INVALID_CALLBACK.
- **AD-14:** ledger `processed_updates` для всех updates; partial unique —
  один активный AiRequest на `inline_message_id`; гранулярность генерации —
  на публикацию.
- **AD-19:** `parse_mode=HTML`; финальный текст (обрезка + sources-блок)
  собирает impl; adapter отправляет as-is; валидация длины query — adapter,
  отказ без обрезки.
- **AD-23:** at-least-once (offset после персистентной обработки);
  `answerCallbackQuery` немедленно при валидном callback; порт
  `InlineMessageEditor`; «message is not modified» = успех; один poller на
  bot token во всех окружениях; webhook — обязательный `secret_token`
  (constant-time), случайный slug-путь, IP allowlist Telegram.
- **AD-8:** `is_personal=true`, `cache_time=0`.
