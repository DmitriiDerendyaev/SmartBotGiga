/**
 * SmartBot Adapter Telegram — входящий/исходящий адаптер Telegram Bot API.
 *
 * <p>Inline Mode: InlineQuery, InlineQueryResultArticle, CallbackQuery,
 * answerInlineQuery, answerCallbackQuery, editMessageText, getUpdates/webhook
 * (Phase 2). Весь исходящий трафик к Telegram — через утверждённый VPN-маршрут
 * (см. docs/architecture/network-routing.md).
 *
 * <p>Границы модуля: реализует порты application-api; не зависит от других
 * адаптеров и application-impl.
 */
package ru.smartbot.adapter.telegram;
