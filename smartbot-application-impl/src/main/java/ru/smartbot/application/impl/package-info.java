/**
 * SmartBot Application Impl — реализация use cases (application services).
 *
 * <p>Оркестрация доменной модели и портов: жизненный цикл AiRequest,
 * регистрация пользователей, идемпотентность, квоты (Phase 3, 6).
 *
 * <p>Границы модуля: зависит от application-api, domain, common.
 * Разрешены spring-context и spring-tx. Запрещены Spring Web,
 * адаптеры и внешние SDK.
 */
package ru.smartbot.application.impl;
