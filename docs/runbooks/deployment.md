# Runbook: Deployment — SmartBot

> Статус: заготовка Phase 1. Актуализируется в Phase 7 после infrastructure
> assessment и утверждения deployment plan.

## Предусловия (checklist перед любым деплоем)

- [ ] Read-only assessment целевого окружения выполнен
- [ ] Deployment plan утверждён владельцем
- [ ] Rollback steps определены и проверены
- [ ] Секреты доступны через Infisical (не в Git/логах)
- [ ] VPN-маршрут для Telegram проверен (Phase 0 report)
- [ ] Сертификат Минцифры в truststore (для GigaChat TLS)

## DEV (develop)

```bash
# на VPS (после настройки Phase 7):
docker compose pull app          # образ из GHCR по тегу develop/sha
docker compose up -d app
curl -fsS localhost:8080/actuator/health
```

## PROD (release)

1. Убедиться, что release-ветка прошла CI (build + tests + image).
2. Получить явное approval владельца.
3. Зафиксировать текущий image tag (для rollback).
4. Deploy pinned sha-тега; smoke test: health + тестовый inline query.
5. Наблюдение: логи, метрики, error rate ≥ 15 минут.

## Rollback

```bash
export SMARTBOT_IMAGE_TAG=sha-<previous>
docker compose up -d app
curl -fsS localhost:8080/actuator/health
```

- Миграции БД: только forward-compatible; откат схемы — отдельный план.

## Запреты

- Не менять VPN-инфраструктуру без разрешения.
- Не claim'ить успех деплоя без verification работающего приложения.
- Не выводить SSH credentials/токены в логи workflow.
