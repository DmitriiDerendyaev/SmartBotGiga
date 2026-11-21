/**
 * SmartBot Adapter Persistence — адаптер персистентности.
 *
 * <p>PostgreSQL + Spring Data JPA + Flyway (Phase 3). UUID — внутренние
 * идентификаторы, BIGINT — Telegram ID. Конфигурация провайдеров хранится
 * отдельно от секретов. Контекст диалогов не хранится.
 *
 * <p>Границы модуля: реализует порты репозиториев из application-api;
 * не зависит от других адаптеров и application-impl.
 */
package ru.smartbot.adapter.persistence;
