/**
 * SmartBot Adapter Search — адаптеры внешнего поиска.
 *
 * <p>Режимы: NONE, NATIVE, EXTERNAL, AUTO (Phase 5). Нормализованные результаты:
 * title, url, snippet, publicationDate, sourceMetadata. Сохранение исходных URL
 * в финальном ответе.
 *
 * <p>Границы модуля: реализует порт SearchProvider из application-api;
 * не зависит от других адаптеров и application-impl.
 */
package ru.smartbot.adapter.search;
