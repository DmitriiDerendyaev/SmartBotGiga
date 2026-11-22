---
title: 'Story 1.1 — Безопасные ошибки и redaction логов'
type: 'feature'
created: '2026-11-21'
status: 'done'
review_loop_iteration: 0
baseline_commit: '3223d9379c10662297120da14fddc9e3027625ee'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Публичный бот не имеет каталога доменных ошибок и механизма маскирования секретов в логах; без них адаптеры могут пробрасывать сырые тела внешних API пользователю, а bot token (часть URL Telegram API) и ключи — попадать в логи/stacktrace (AD-4, AD-20, FR24).

**Approach:** Единый enum `ErrorCode` в domain + доменное исключение без сырых payload; единственная таблица ErrorCode→безопасный текст в application-api; logback masking converter (message + stacktrace) в infrastructure, подключаемый в app; ArchUnit-страж против `getMessage()` в адаптерах.

## Boundaries & Constraints

**Always:** каталог ErrorCode точно по AD-20 (15 значений); domain/application-api — без Spring (enforcer уже banned); маскирование покрывает message, stacktrace и MDC во всех appender'ах; таблица ErrorCode→текст — единственная, adapter не содержит своего маппинга; тексты безопасные, на русском, без внутренних деталей.

**Ask First:** изменение состава ErrorCode (требует amendment spine AD-20); ослабление ArchUnit-стража.

**Never:** сырые внешние тела/заголовки в доменных исключениях; `getMessage()` для user-facing текста в адаптерах; логирование PII на INFO; trust-all/отключение TLS; реальные секреты в тестах (только фиктивные).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Каталог полон | любой ErrorCode | SafeErrorMessages возвращает непустой безопасный текст | unit-тест: все 15 значений покрыты |
| Доменное исключение | ErrorCode + безопасные параметры | DomainException несёт код и типизированные параметры | сырые payload не принимаются конструктором |
| Bot token в логе | сообщение/stacktrace с `bot123456:ABC-DEF_x` | `bot[REDACTED]` во всех appender'ах | маскирование до вывода |
| Authorization/access_token | `Authorization: Bearer xyz`, `"access_token":"abc"` | значения → `[REDACTED]` | регистронезависимо |
| PrivateKey блок | `-----BEGIN PRIVATE KEY-----…-----END…` | весь блок → `[REDACTED PRIVATE KEY]` | multiline |
| Чистое сообщение | текст без секретов | не изменяется | N/A |

</frozen-after-approval>

## Code Map

- `smartbot-domain/src/main/java/ru/smartbot/domain/` -- пусто (package-info); сюда `error/ErrorCode.java`, `error/DomainException.java`
- `smartbot-application-api/src/main/java/ru/smartbot/application/api/` -- сюда `error/SafeErrorMessages.java` (чистая Java, static Map)
- `smartbot-infrastructure/src/main/java/ru/smartbot/infrastructure/` -- сюда `logging/SecretMaskingConverter.java` (logback classic MessageConverter) и `logging/MaskingThrowableProxyConverter.java`; logback-classic доступен через spring-boot-starter
- `smartbot-app/src/main/resources/` -- application.yml есть; сюда `logback-spring.xml` (conversionRule + pattern `%maskedMsg %maskedEx`)
- `smartbot-app/src/test/java/ru/smartbot/app/architecture/ArchitectureBoundariesTest.java` -- существующие 6 правил; добавить страж `noClasses adapter.. call Throwable.getMessage()`
- `smartbot-app/pom.xml` -- test-зависимости уже есть (spring-boot-starter-test, archunit)
- Read-only: pom-границы модулей (enforcer bannedDependencies), spine AD-20/AD-4

## Tasks & Acceptance

**Execution:**
- [x] `smartbot-domain/.../error/ErrorCode.java` -- enum 15 значений по AD-20 -- единый каталог
- [x] `smartbot-domain/.../error/DomainException.java` -- RuntimeException с ErrorCode + Map безопасных типизированных параметров -- сырые payload не пересекают порт
- [x] `smartbot-application-api/.../error/SafeErrorMessages.java` -- единственная таблица код→безопасный текст (RU) -- adapter не маппит сам
- [x] `smartbot-infrastructure/.../logging/SecretMaskingConverter.java` + `MaskingThrowableProxyConverter.java` -- маскирование паттернов: bot token, Authorization/access_token/refresh_token/password/secret/api_key, PrivateKey блоки -- AD-4
- [x] `smartbot-app/src/main/resources/logback-spring.xml` -- conversionRule maskedMsg/maskedEx, console pattern с ними -- покрытие всех appender'ов
- [x] `smartbot-domain/src/test/.../ErrorCodeTest.java`, `smartbot-application-api/src/test/.../SafeErrorMessagesTest.java` -- полнота каталога и непустые тексты -- unit без Spring
- [x] `smartbot-infrastructure/src/test/.../SecretMaskingConverterTest.java` -- unit-тест на каждый паттерн I/O-матрицы (фиктивные секреты) -- regression
- [x] `ArchitectureBoundariesTest.java` -- страж: adapter-классы не вызывают Throwable.getMessage() -- user-facing путь только через ErrorCode

**Acceptance Criteria:**
- Given любое доменное исключение, when адаптер маппит внешнюю ошибку, then наружу только ErrorCode + безопасные параметры (конструктор не принимает сырые тела)
- Given сообщение/stacktrace/MDC с секретом, when логируется, then значение замаскировано до вывода в любом appender'е
- Given ArchUnit-прогон, when adapter вызывает getMessage(), then тест падает
- Given `make verify`, when полный прогон, then BUILD SUCCESS, все тесты зелёные

## Spec Change Log

- 2026-11-22 (category patch по review-находкам, frozen-секции не менялись):
  - P1: страж `adaptersNeverCallGetMessage` расширен на `getLocalizedMessage()`/`toString()`/`printStackTrace()` (Throwable-assignable owner'ы); в javadoc задокументированы ограничения (invokedynamic-конкатенация/String.valueOf не ловятся; port-shape отложен). Негативная проверка: временный класс с тремя вызовами в adapter-telegram → страж падает (3 violations), класс удалён.
  - P2: SecretMasker закрыл bypass'ы: escaped JSON (`\"password\":\"…\"`, `\"Authorization\":\"Bearer …\"`), hyphenated `api-key`/`X-Api-Key`, `private_key`/`id_token`/`bot_token`/bare `token=`/`passwd`/`pwd` (+camelCase), standalone `Bearer <jwt>`/`Basic <b64>`, credentials в URL (`scheme://user:pass@host`, JDBC `password=`), PGP PRIVATE KEY BLOCK, quoted-значения с пробелами, folded Authorization (продолжение с отступом; stacktrace-кадры не поглощаются). Ограничения и fail-closed unterminated PEM задокументированы в javadoc.
  - P3: MaskingMdcConverter маскирует bare-values `%X{key}` (Bearer/Basic/bot-token); тесты на `%X{key}`.
  - P4: добавлен `smartbot-app/src/main/resources/logback.xml` (идентичные conversionRules и masked-pattern, pre-Spring окно); wiring-тест проверяет оба конфига; комментарий про StaticLoggerBinder заменён на актуальный (SLF4J ServiceLoader). Атрибут `converterClass` заменён на `class` (logback 1.6.x deprecation WARN).
  - P5: новый `LogbackConfigGuardTest` — парсит оба конфига, падает на raw conversion words (%msg/%message/%m/%ex/%exception/%throwable/%xEx/%xException/%xThrowable/%nopex/%nopexception/%rEx/%rootException) в appender patterns; проверяет наличие masking-conversionRules (включая mdc/X) и использование %maskedMsg/%maskedEx.
  - P6: DomainException — final; javadoc: параметры только для логов, пользователю не рендерятся; тесты NPE на null-ключ/значение и serialization round-trip.
  - P7: тесты parameterized logging (`log.info("token {}", secret)`), suppressed exceptions, PEM idempotency, unterminated PEM fail-closed; прямые unit-тесты SecretMasker (SecretMaskerTest, 32 теста).
  - P8: LogbackMaskingWiringTest — `@ResourceLock(Resources.SYSTEM_OUT)`; Joran-статусы проверяются осознанно: ERROR и WARN запрещены (новый WARN требует разбора). Проверка сработала: deprecation WARN `converterClass` → конфиги исправлены на `class`.
  - P9: SafeErrorMessagesTest — `doesNotContainIgnoringCase(code.name())`; uniqueness текстов задокументирована как project convention.
  - Поведенческое уточнение: MDC-ключи, сами являющиеся секретными (`botToken` и т.п.), теперь дают полное `[REDACTED]` значения (`botToken=[REDACTED]`) — маскирование строже, чем `bot[REDACTED]`; ожидания wiring/MDC-тестов обновлены.

## Verification

**Commands:**
- `make verify` -- expected: BUILD SUCCESS; новые unit-тесты маскирования/каталога зелёные; ArchUnit (включая новый страж) зелёный

**Results (2026-11-21, baseline 3223d93):**
- `make verify` -- BUILD SUCCESS; 49 тестов зелёные: domain 7 (ErrorCodeTest 2, DomainExceptionTest 5), application-api 19 (SafeErrorMessagesTest), infrastructure 15 (SecretMaskingConverterTest), app 8 (ArchitectureBoundariesTest 7, LogbackMaskingWiringTest 1).
- Страж проверен негативным тестом: временный класс с `Throwable.getMessage()` в adapter-telegram → `adaptersNeverCallGetMessage` падает с Architecture Violation (остальные 6 правил зелёные); класс удалён, повторный прогон зелёный.
- Реализация MDC-покрытия (frozen «Always»): добавлены `MaskingMdcConverter` и общий `SecretMasker` (infrastructure/logging); в logback-spring.xml conversionRule переопределяет встроенные `mdc`/`X`. Wiring-тест грузит реальный logback-spring.xml в изолированный LoggerContext и подтверждает маскирование message/stacktrace/MDC до вывода в console appender (logback 1.6.6).
- DomainException: конструкторы принимают только ErrorCode + Map<String,String> (без raw message/cause); getMessage() = имя кода; параметры — защитная неизменяемая копия. SafeErrorMessages: fail-closed IllegalStateException при отсутствии записи (без fallthrough).

**Results (2026-11-22, category patch по review):**
- `make verify` — BUILD SUCCESS; 92 теста зелёные: domain 9 (ErrorCodeTest 2, DomainExceptionTest 7), application-api 19 (SafeErrorMessagesTest), infrastructure 51 (SecretMaskerTest 32, SecretMaskingConverterTest 19), app 13 (ArchitectureBoundariesTest 7, LogbackConfigGuardTest 4, LogbackMaskingWiringTest 2 — по обоим конфигам).
- Расширенный страж проверен негативным тестом: временный класс с `printStackTrace()`/`toString()`/`getLocalizedMessage()` в adapter-telegram → `adaptersNeverCallGetMessage` падает (3 violations, остальные 6 правил зелёные); класс удалён, повторный `make verify` зелёный.
- Осознанная WARN-проверка Joran выявила deprecation `converterClass` в обоих конфигах → заменён на `class` (logback 1.6.x), повторный прогон без WARN/ERROR.

## Suggested Review Order

**Каталог ошибок и безопасный контракт (AD-20)**

- Единый enum 15 кодов — ядро контракта, всё остальное опирается на него
  [`ErrorCode.java:14`](../../smartbot-domain/src/main/java/ru/smartbot/domain/error/ErrorCode.java#L14)
- final-исключение: только код + log-only параметры, сырые payload не пересекают порт
  [`DomainException.java:22`](../../smartbot-domain/src/main/java/ru/smartbot/domain/error/DomainException.java#L22)
- Единственная таблица код→безопасный текст, fail-closed при пропуске
  [`SafeErrorMessages.java:19`](../../smartbot-application-api/src/main/java/ru/smartbot/application/api/error/SafeErrorMessages.java#L19)

**Маскирование секретов (AD-4)**

- Pattern-engine: bot token, Authorization, key-values, URL creds, PEM/PGP, escaped JSON
  [`SecretMasker.java:46`](../../smartbot-infrastructure/src/main/java/ru/smartbot/infrastructure/logging/SecretMasker.java#L46)
- conversionRules maskedMsg/maskedEx + override mdc/X — покрытие всех appender'ов
  [`logback-spring.xml:13`](../../smartbot-app/src/main/resources/logback-spring.xml#L13)

**Enforcement**

- Страж: адаптеры не вызывают getMessage/getLocalizedMessage/toString/printStackTrace
  [`ArchitectureBoundariesTest.java:127`](../../smartbot-app/src/test/java/ru/smartbot/app/architecture/ArchitectureBoundariesTest.java#L127)
- Config-guard: raw conversion words в любом appender'е падают тестом
  [`LogbackConfigGuardTest.java:1`](../../smartbot-app/src/test/java/ru/smartbot/app/logging/LogbackConfigGuardTest.java#L1)

**Периферия**

- Wiring-тест обоих конфигов (pre-Spring logback.xml + Spring logback-spring.xml)
  [`LogbackMaskingWiringTest.java:1`](../../smartbot-app/src/test/java/ru/smartbot/app/logging/LogbackMaskingWiringTest.java#L1)
