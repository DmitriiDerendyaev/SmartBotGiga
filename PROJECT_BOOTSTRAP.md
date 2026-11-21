# PROJECT BOOTSTRAP — SmartBot

Статус инициализации проекта по `initSpec.md` (BMAD-процесс, сокращённая цепочка:
Architecture → Epics/Stories → Build).

## Текущий статус

| Фаза | Статус | Комментарий |
|---|---|---|
| Phase 0 — Discovery & Infrastructure Assessment | ⏳ ожидает данные пользователя | Epic 5 gate: VPS/SSH/VPN/bot tokens |
| Phase 1 — Project Bootstrap | ✅ завершена | BUILD SUCCESS, push main+develop |
| Phase 2 — Telegram Adapter | 🔄 Epic 1 (локально, WireMock) | реальная верификация — Epic 5 |
| Phase 3 — User & Request Management | 🔄 Epic 1–2 | |
| Phase 4 — GigaChat Provider | ⏸ Epic 2 (WireMock до credentials) | |
| Phase 5 — Search Integration | ⏸ Epic 3 | провайдер не выбран (Phase F) |
| Phase 6 — Async Processing | ⏸ Epic 1.6 + Epic 2 | |
| Phase 7 — Production Readiness | ⏸ Epic 4–5 | |

## BMAD артефакты

- Architecture Spine (final): `_bmad-output/planning-artifacts/architecture/architecture-SmartBot-2026-11-20/ARCHITECTURE-SPINE.md`
  — AD-1…AD-26; 4 независимых ревью (rubric/versions/adversary/security), все фиксы применены; lint 0 findings.
- PRD (derived из initSpec): `_bmad-output/planning-artifacts/PRD.md` — 25 FR / 14 NFR.
- Epics & Stories: `_bmad-output/planning-artifacts/epics.md` — 5 epics / 21 stories, валидация пройдена.
- Reviews: `…/architecture-SmartBot-2026-11-20/reviews/`.

## Выполнено (Phase 1)

- [x] Git-репозиторий инициализирован; remote: `github.com/DmitriiDerendyaev/SmartBotGiga` (пустой, проверен через `git ls-remote`)
- [x] BMAD установлен: Core + BMM v6.11.0 (`_bmad/`, `.agents/skills`, `.opencode/commands`)
- [x] Maven multi-module: 10 модулей по §7 спеки, Java 21, Spring Boot 4.2.0 (версии проверены по Maven Central 2026-11-20)
- [x] Архитектурные границы: ArchUnit-тесты (domain без Spring, изоляция адаптеров)
- [x] Flyway baseline migration (`V1__baseline.sql`, pgcrypto)
- [x] Docker: multi-stage Dockerfile (JDK 21 build → JRE 21 runtime), compose dev/full
- [x] Makefile: сборка/тесты через Docker (локальный JDK не требуется)
- [x] GitHub Actions CI: build+test на feature/*/PR; образ в GHCR на develop/release
- [x] Документация: README, скелет docs/ по §15
- [x] Первый commit + push (main + develop; BUILD SUCCESS, ArchUnit 6/6)
- [x] BMAD Architecture Document (spine final, AD-1…AD-26)
- [x] BMAD Epics & Stories (5 epics / 21 stories)

## Решения, зафиксированные на бутстрапе

1. **Сборка через Docker** (`maven:3.9-eclipse-temurin-21`), локальный JDK не устанавливается (выбор пользователя).
2. **Документация на русском**, англоязычные термины сохраняются (выбор пользователя).
3. **BMAD сокращённая цепочка**: initSpec.md уже содержит product brief и продуктовые решения → сразу Architecture + Epics/Stories (выбор пользователя).
4. **application-api без Spring** — более строгая граница, чем требует спека (спека запрещает только зависимости от Telegram/GigaChat реализаций).
5. **Telegram-клиент**: планируется Spring WebClient (а не готовая SDK-библиотека) — упрощает контроль VPN-маршрута на уровне HTTP-клиента. Финальное решение — в ADR после Phase 0.
6. **Redis** — опциональный compose profile `cache`, решение об использовании отложено до обоснования (rate limiting / token cache).

## Infrastructure Discovery Checklist (Phase 0)

### Repository (Phase A) — ✅ частично
- [x] URL: https://github.com/DmitriiDerendyaev/SmartBotGiga.git
- [x] Owner: DmitriiDerendyaev (gh auth подтверждён, SSH)
- [x] Репозиторий пуст — inspection выполнен
- [ ] Branching strategy подтверждена пользователем (feature/*, develop, release)

### VPS & SSH (Phase B) — ⏳
- [ ] IP/hostname, SSH port, username
- [ ] ОС и версия
- [ ] Метод аутентификации (SSH agent / локальный ключ; НЕ в чат)
- [ ] Docker установлен?
- [ ] CPU / RAM / disk

### Amnezia VPN (Phase C) — ⏳
- [ ] Протокол (НЕ предполагать: WireGuard / AmneziaWG / OpenVPN / другое)
- [ ] Endpoint, port
- [ ] Клиентская конфигурация (secure transfer)
- [ ] Доступен ли VPN с целевого VPS
- [ ] Текущая routing-конфигурация
- [ ] Тест: Telegram Bot API через VPN
- [ ] Тест: GigaChat напрямую (DNS, TLS, сертификат Минцифры)

### Telegram Bot (Phase D) — ⏳
- [ ] Display name, username
- [ ] Inline Mode включён (BotFather)
- [ ] Токен — через Infisical (НЕ в чат)

### GigaChat (Phase E) — ⏳
- [ ] Authorization Key — через Infisical
- [ ] Scope: PERS / B2B / CORP
- [ ] Список доступных моделей (проверить через API)
- [ ] Тарифные лимиты

### Search (Phase F) — ⏳
- [ ] Провайдер не выбран; абстракция готовится, опции документируются

## Blockers

1. **Phase 0 не проведена** — нельзя стартовать Phase 2 (Telegram) до проверки VPN-маршрута.
2. Нет данных VPS/SSH и Amnezia VPN.
3. Не выбран внешний search-провайдер (не блокирует Phase 1–4).

## Security notes

- Секреты не запрашивались и не хранятся в репозитории.
- `.gitignore` покрывает `.env`, ключи, VPN-конфиги.
- CI: `GITHUB_TOKEN` только для GHCR; deploy-секреты появятся через GitHub Environments/Infisical в Phase 7.
