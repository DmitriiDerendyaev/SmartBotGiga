/**
 * SmartBot Infrastructure — сквозная инфраструктура.
 *
 * <p>Конфигурация, метрики (Micrometer), per-user rate limiting, quotas,
 * global LLM budget, redaction чувствительных данных в логах, интеграция
 * с Infisical (Phase 7).
 *
 * <p>Границы модуля: зависит от application-api/impl, domain, common;
 * не зависит от адаптеров (wiring — в smartbot-app).
 */
package ru.smartbot.infrastructure;
