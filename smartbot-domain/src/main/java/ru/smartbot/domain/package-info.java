/**
 * SmartBot Domain — доменная модель (Hexagonal Architecture, ядро).
 *
 * <p>Сущности: User, UserSettings, AiRequest, AiProvider, SearchProvider,
 * AiRequestSearch, AiRequestUsage (реализация — Phase 3).
 *
 * <p>Границы модуля: зависит только от smartbot-common и JDK.
 * Запрещены Spring, JPA, Telegram/GigaChat SDK и любые внешние фреймворки.
 */
package ru.smartbot.domain;
