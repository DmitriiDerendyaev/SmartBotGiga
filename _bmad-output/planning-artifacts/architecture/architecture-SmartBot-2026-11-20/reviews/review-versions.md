# Review: Technology Currency — SmartBot Architecture Spine & POMs

- **Дата проверки:** 2026-11-20 (live-проверка выполнена 2026-11-21)
- **Reviewer:** independent technology-currency reviewer (subagent)
- **Scope:** `ARCHITECTURE-SPINE.md` (Stack, AD-1/6/9/11), `pom.xml` (parent), все `smartbot-*/pom.xml`, `Dockerfile`, `docker-compose*.yml`, `smartbot-app/src/main/resources/application.yml`
- **Метод:** live-источники — repo1.maven.org (`maven-metadata.xml`, BOM pom), api.spring.io, github.com (Spring Boot wiki), hub.docker.com API, developers.sber.ru, прямой probe `api.giga.chat`
- **Вердикт:** ✅ **PASS** — все зафиксированные версии существуют, стабильны и пригодны. Блокеров нет. 5 неблокирующих замечаний (advisory).

---

## 1. Проверенные решения (PASS)

### 1.1 Spring Boot 4.2.0 + Java 21

| Факт |Evidence | Статус |
| --- | --- | --- |
| 4.2.0 существует на Maven Central | `spring-boot-dependencies/maven-metadata.xml`: `<latest>4.2.0</latest>`, `<release>4.2.0</release>`; хвост: 4.1.3 → 4.2.0-M1/M2 → RC1/RC2 → **4.2.0** | ✅ |
| 4.2.0 — stable GA, текущий релиз | api.spring.io `/projects/spring-boot/releases`: `4.2.0 — GENERAL_AVAILABILITY, current=true` (4.0.10 и 4.1.3 — GA, не current; 4.2.1-SNAPSHOT в работе) | ✅ |
| Java baseline Boot 4.x | Spring Boot 4.0 Migration Guide (github wiki): **«Spring Boot 4.0 requires Java 17 or later. Using the latest LTS release of Java is encouraged.»** Baseline не повышался в 4.1/4.2 | ✅ Java 21 (LTS) совместим; `maven.compiler.release=21`, enforcer `[21,)` корректны |
| BOM-импорт в parent pom | `spring-boot-dependencies:4.2.0` import — валидная замена parent; `spring-boot-maven-plugin` 4.2.0 существует | ✅ |

Managed-версии из `spring-boot-dependencies-4.2.0.pom` (фактически прочитан):
`spring-framework 7.1.0`, `hibernate 7.4.15.Final`, `flyway 12.11.0`, `testcontainers 2.0.5`, `junit-jupiter 6.1.3`, `micrometer 1.18.0`, `postgresql 42.7.14`, `mockito 5.24.0`, `assertj 3.27.7`.

### 1.2 ArchUnit 1.5.1 (AD-1)

- `com.tngtech.archunit:archunit-junit5/maven-metadata.xml`: `<release>1.5.1</release>` — **последний стабильный релиз** (хвост: 1.4.2 → 1.5.0 → 1.5.1). ✅
- Артефакт `archunit-junit5` 1.5.1 существует; aggregator тянет `archunit-junit5-api`/`-engine` 1.5.1. ✅
- ⚠️ См. advisory A-3: BOM Boot 4.2 управляет JUnit **6**.1.3, а не 5; связку ArchUnit-junit5 + JUnit 6 проверить первым же прогоном `ArchitectureBoundariesTest` (SPI `TestEngine` стабилен, риск низкий).

### 1.3 WireMock 3.13.2 (stable vs 4.x beta)

- `org.wiremock:wiremock-standalone/maven-metadata.xml`: 3.13.2 существует; хвост stable-ветки 3.x: 3.13.0 → 3.13.1 → **3.13.2** — последний стабильный 3.x. ✅
- 4.x: последний — **4.0.0-beta.44**; стабильных 4.x релизов **нет** (проверено фильтром по всем версиям). Решение spine «3.13.2 stable; 4.x — beta, не используется» **актуально и верно**. ✅

### 1.4 Testcontainers 2.x renames + BOM (AD-9, тесты persistence)

- Boot 4.2.0 BOM: `<testcontainers.version>2.0.5</testcontainers.version>`, импорт `testcontainers-bom:${testcontainers.version}` → **2.0.5**. ✅
- `org.testcontainers:testcontainers-bom`: release **2.0.5** (линейка 1.21.4 → 2.0.0…2.0.5). ✅
- Новые artifactIds 2.x существуют и управляются BOM:
  - `org.testcontainers:testcontainers-postgresql` — release 2.0.5 ✅ (бывший `org.testcontainers:postgresql`)
  - `org.testcontainers:testcontainers-junit-jupiter` — release 2.0.5 ✅ (бывший `org.testcontainers:junit-jupiter`)
- `smartbot-adapter-persistence/pom.xml` использует **оба новых имени** + `org.springframework.boot:spring-boot-testcontainers` (4.2.0 существует, release 4.2.0). Версии не пинятся — корректно наследуются из BOM. ✅
- Boot 4.0 Release Notes подтверждают переход на «TestContainers 2.0». ✅

### 1.5 Стартеры и Flyway в Boot 4.2.0

Все проверены по `maven-metadata.xml` (каждый имеет `<release>4.2.0</release>`):

| Артефакт | Где используется | Статус |
| --- | --- | --- |
| `spring-boot-starter-web` | smartbot-app | ✅ валиден (servlet/Tomcat 11; модуляризация Boot 4 имён стартеров не меняла) |
| `spring-boot-starter-webflux` | adapter-telegram / llm / search | ✅ валиден (WebClient в servlet-приложении — штатно) |
| `spring-boot-starter-data-jpa` | adapter-persistence | ✅ валиден (Hibernate 7.4, Jakarta Persistence 3.2) |
| `spring-boot-starter-actuator` | smartbot-app | ✅ валиден |
| `spring-boot-starter-test` | smartbot-app | ✅ валиден |
| `flyway-core` + `flyway-database-postgresql` | adapter-persistence | ✅ **`flyway-database-postgresql` по-прежнему нужен**: Flyway 12.x (managed 12.11.0) сохраняет модульную структуру БД-модулей; артефакт жив (release 13.13.0 в линейке, 12.11.0 управляется BOM) |
| `micrometer-registry-prometheus` | smartbot-app | ✅ managed (Micrometer 1.18.0) |

### 1.6 application.yml — переименования свойств Boot 4.x

Сверено с официальным **Spring Boot 4.0 Configuration Changelog** (github wiki, 3.5.8 → 4.0.0):

| Свойство в skeleton | Статус в Boot 4.2 |
| --- | --- |
| `spring.jpa.hibernate.ddl-auto: validate` | ✅ не менялось (в changelog отсутствует; AD-9 соблюдён) |
| `spring.jpa.open-in-view: false` | ✅ не менялось |
| `spring.flyway.enabled` / `locations` | ✅ не менялись (удалён только `clean-on-validation-error` — не используется) |
| `management.endpoints.web.exposure.include: health,info,prometheus` | ✅ не менялось |
| `management.endpoint.health.probes.enabled: true` | ✅ **уже новое каноническое имя**: старое `management.health.probes.enabled` удалено в 4.0, замена — `management.endpoint.health.probes.enabled`. Skeleton использует правильное. |
| `server.port`, `server.shutdown: graceful`, `logging.level.*` | ✅ не менялись |

Релевантные breaking changes Boot 4.0, которых skeleton **не касается** (проверено по changelog): `management.endpoint.*.enabled → .access`, `server.error.* → spring.web.error.*`, `spring.http.client.* → spring.http.clients.*`, `management.tracing.enabled → management.tracing.export.enabled`, Jackson 2 → **Jackson 3** по умолчанию (`spring.jackson2.*` deprecated). Для будущего кода адаптеров (Phase 2/4/5) помнить про Jackson 3 при JSON-маппинге WebClient.

### 1.7 Docker-образы (AD-11)

| Образ | Проверка (hub.docker.com API) | Статус |
| --- | --- | --- |
| `maven:3.9-eclipse-temurin-21` (Dockerfile build stage) | тег существует, HTTP 200, last_updated 2026-09-26 | ✅ валиден; Maven 3.9 + JDK 21 соответствует AD-11 и enforcer `[3.9,)` |
| `eclipse-temurin:21-jre` (runtime stage) | тег существует, last_updated 2026-11-20 | ✅ валиден, свежий |
| `postgres:16-alpine` (compose dev/prod) | тег существует и обновляется (16.16-alpine) | ✅ валиден, см. advisory A-1 |
| `redis:7-alpine` (compose, опционально) | тег существует (7.2.17-alpine) | ✅ валиден, см. advisory A-2 |

### 1.8 GigaChat API (AD-6)

- **Docs доступны** (гео-блокировки не случилось): `https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/gigachat-api` → HTTP 200. Текст: **«С 17 июля 2026 года для подключения к GigaChat API используется адрес — `https://api.giga.chat`. Он является целевым URL для всех пользователей: физических и юридических лиц.»** → base URL в AD-6 **актуален и является целевым** (старые адреса — legacy для подключившихся до 17.07.2026). ✅
- **Endpoint проверен живым probe:** `POST https://api.giga.chat/v1/chat/completions` (без авторизации) → **HTTP 401 `{"status":401,"message":"Unauthorized"}`** — маршрут существует, требует OAuth access token. Это подтверждает `POST /v1/chat/completions` без необходимости читать JS-рендеренную часть docs. ✅
- Справочная страница `.../rest/post-chat` (HTTP 200) упоминает **`POST /v2/chat/completions`** — вариант для параллельного function calling (`model_options.parallel_tool_calls`, только GigaChat-3-Ultra). Live probe `/v2/chat/completions` → тоже 401 (маршрут существует). См. advisory A-5.
- OAuth/RqUID/TLS-требования AD-6 не противоречат проверяемым фактам; список моделей и scope аккаунта остаются Phase E (Open Question 3) — вне рамок version-review.

---

## 2. Advisory (неблокирующие)

### A-1. PostgreSQL 16 → текущие мажоры 17/18 (рекомендуется рассмотреть)
Docker Hub: `16-alpine` = 16.16 (обновляется), но текущие мажоры — **17** (17.12-alpine) и **18** (18.7-alpine); в реестре уже есть и 19.0/19rc2-alpine. PG 16 поддерживается до **ноября 2028** (EOL), т.е. решение не срочное. Для нового проекта без legacy-данных разумно стартовать с `postgres:17-alpine` или `18-alpine` (Flyway 12 / pgjdbc 42.7.14 / Testcontainers 2.0.5 их поддерживают). **Не блокер** — 16-alpine валиден и свеж по патчам.

### A-2. Redis 7-alpine → 7.2.17; текущая линейка 8.x
`redis:7-alpine` указывает на 7.2.17; доступны 7.4.7 и 8.x (8.6.7-alpine). Redis в проекте **опционален** (Deferred: «Redis: использовать или нет»). При активации (rate limit / token cache, Phase 7) рассмотреть `redis:7.4-alpine` или `8-alpine`; для простого кэша/лимитов 7.2 достаточен. **Не блокер.**

### A-3. Spine говорит «JUnit 5 (managed)», но Boot 4.2.0 управляет JUnit 6
BOM 4.2.0: `junit-jupiter.version = 6.1.3` (JUnit 6 — преемник JUnit 5, те же координаты `org.junit.jupiter:junit-jupiter`, совместимый Jupiter API). Строка Stack «JUnit | 5 (managed)» — **документационный дрейф**; фактический тестовый стек будет JUnit 6. Действие: поправить формулировку spine на «JUnit Jupiter (managed by Boot BOM; 6.x)» и первым прогоном тестов проверить связку ArchUnit-junit5 1.5.1 / Mockito 5.24 / AssertJ 3.27 под JUnit 6 (риск низкий: TestEngine SPI и Jupiter API сохранены). **Не блокер.**

### A-4. Maven-плагины слегка отстают от последних stable
| Плагин | В pom | Последний stable | Примечание |
| --- | --- | --- | --- |
| maven-compiler-plugin | 3.13.0 | **3.16.0** | 4.0.0 — пока beta (4.0.0-beta-5), не брать |
| maven-surefire / failsafe | 3.5.2 | **3.6.1** | |
| maven-enforcer-plugin | 3.5.0 | **3.7.0** | |
Работоспособны, обновить при ближайшем случае. **Не блокер.**

### A-5. GigaChat: существует `/v2/chat/completions` (parallel tool calls)
AD-6 фиксирует `POST /v1/chat/completions` — **валиден** (подтверждено live). Docs описывают `/v2/chat/completions` для параллельного function calling (только GigaChat-3-Ultra). Для Phase 4/5 (search + function calling) знать о варианте; менять AD-6 сейчас не требуется. **Информационно.**

### Прочее (наблюдения, без действий)
- Flyway: managed 12.11.0 при последнем 13.13.0 — **правильно** следовать BOM Boot, не пинить вручную.
- Hibernate 7.4 (Boot 4) — `ddl-auto=validate` и Flyway-forward-only (AD-9) совместимы.
- Boot 4.0 перешёл на Jackson 3 по умолчанию — учесть при реализации JSON в адаптерах (WebClient/GigaChat DTO); deprecated `spring.jackson2.*` не использовать.
- Утверждение spine «Версии проверены по repo1.maven.org и spring.io 2026-11-20» — **подтверждается** повторной live-проверкой.

---

## 3. Источники (все запрошены live)

1. `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml` (+ pom 4.2.0)
2. `https://repo1.maven.org/maven2/com/tngtech/archunit/archunit-junit5/maven-metadata.xml` (+ pom archunit-junit5-engine 1.5.1)
3. `https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/maven-metadata.xml`
4. `https://repo1.maven.org/maven2/org/testcontainers/{testcontainers-bom,testcontainers-postgresql,testcontainers-junit-jupiter}/maven-metadata.xml`
5. `https://repo1.maven.org/maven2/org/springframework/boot/{spring-boot-starter-web,-webflux,-data-jpa,-actuator,-testcontainers}/maven-metadata.xml`
6. `https://repo1.maven.org/maven2/org/flywaydb/flyway-database-postgresql/maven-metadata.xml`
7. `https://api.spring.io/projects/spring-boot/releases`
8. `https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes`
9. `https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide` (Java baseline)
10. `https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Configuration-Changelog`
11. `https://hub.docker.com/v2/repositories/library/{postgres,redis,maven,eclipse-temurin}/tags...`
12. `https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/gigachat-api` и `.../rest/post-chat`
13. Live probe: `POST https://api.giga.chat/v1/chat/completions` → 401; `POST .../v2/chat/completions` → 401
14. `https://repo1.maven.org/maven2/org/apache/maven/plugins/{maven-compiler-plugin,maven-surefire-plugin,maven-enforcer-plugin}/maven-metadata.xml`

## 4. Итог

**PASS.** Стек spine и pom-ы согласованы с реальностью Maven Central / Docker Hub / spring.io на дату проверки: Boot 4.2.0 GA (Java 17+ baseline → Java 21 OK), ArchUnit 1.5.1 — latest, WireMock 3.13.2 — latest stable (4.x всё ещё beta), Testcontainers-артефакты переименованы корректно под BOM 2.0.5, стартеры и свойства application.yml валидны для Boot 4.2, образы maven/temurin валидны, GigaChat base URL и endpoint подтверждены docs + live-probe. Рекомендации A-1…A-5 — неблокирующие; A-3 (формулировка «JUnit 5») желательно поправить в spine при следующем обновлении.
