# ADR-0003: Сборка через Docker (без локального JDK)

- **Статус**: Accepted
- **Дата**: 2026-11-20

## Context

На машине разработчика нет локальных Java/Maven; установлен Docker 29.x.
Проект требует Java 21 + Maven 3.9.

## Decision

- Все Maven-операции выполняются в контейнере `maven:3.9-eclipse-temurin-21`
  через `Makefile` (`make build/test/verify/package`), с persistent volume
  `smartbot-m2` для кэша зависимостей.
- Integration tests (Testcontainers): проброс `/var/run/docker.sock` в
  Maven-контейнер (`make verify`).
- Production-образ: multi-stage Dockerfile (build JDK 21 → runtime JRE 21,
  non-root user).
- CI (GitHub Actions) использует нативный `setup-java` temurin 21 — там
  Docker-обёртка не нужна.

## Consequences

- ✅ Воспроизводимое окружение сборки, нет дрейфа версий JDK.
- ✅ Нулевая локальная установка.
- ⚠️ Первый запуск медленнее (загрузка образов/зависимостей).
- ⚠️ Testcontainers внутри контейнера требует docker.sock (учтено в Makefile).
- ⚠️ IDE-поддержка Java ограничена без локального JDK; при необходимости
  установить JDK 21 позже (не меняет процесс сборки).
