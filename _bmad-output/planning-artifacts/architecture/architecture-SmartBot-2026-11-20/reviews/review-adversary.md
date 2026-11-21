---
name: 'SmartBot — Adversarial Architecture Review'
type: architecture-review
lens: adversary (compatible-story pairs)
target: ARCHITECTURE-SPINE.md (2026-11-20, draft)
sources: [initSpec.md, ARCHITECTURE-SPINE.md, docs/api/contracts.md, docs/database/er-diagram.md, docs/architecture/telegram-integration.md, docs/architecture/components.md]
created: '2026-11-21'
verdict: 'REVISE — структурный каркас прочный, поведенческие швы не зафиксированы; 12 дыр, из них 5 блокирующих декомпозицию на epics/stories'
---

# Adversarial Review — Architecture Spine SmartBot

## Метод

Для каждой дыры построена пара гипотетических work units одного уровня вниз
(epic/story), каждая из которых **буквально соблюдает все AD-1…AD-12**, но
результаты их работы несовместимы (данные, контракты, владение состоянием).
Если такая пара существует — инвариант не зафиксирован spine'ом, и две
параллельные stories легально построят разные системы.

Важно: `docs/api/contracts.md` и `docs/database/er-diagram.md` помечены как
эскизы/черновики и прямо откладывают финальные контракты в Phase 3–5 stories.
Spine binds на `docs/architecture/*` есть, но эти документы дыры не закрывают —
они их наследуют. Все ссылки на ER ниже — на черновик `er-diagram.md`, который
stories вправе трактовать по-разному, пока AD не зафиксировал форму.

Что spine уже закрывает хорошо (не атакуется): границы модулей и ArchUnit
(AD-1/2), UUID/BIGINT/Flyway (AD-9), секреты в хранении и конфигурации (AD-4),
сетевое разделение (AD-5), TLS/MinTsifry truststore (AD-6), запрет контекста
диалогов (AD-9). Дыры — в **поведенческих контрактах на швах**, а не в структуре.

Легенда Severity: **BLOCKER** — сломает интеграцию epics или данные;
**HIGH** — сломает UX/деньги/безопасность при интеграции;
**MEDIUM** — расхождение, обнаруживаемое на review, но дешевле зафиксировать сейчас.

---

## H1. Callback correlation contract: payload, query delivery, result id [BLOCKER]

**Пара:** «Telegram adapter story (Phase 2, static mock)» vs «Async generation story (Phase 6)».

- **Story A** соблюдает AD-8: мгновенный `InlineQueryResultArticle` с кнопкой
  Generate Answer, `is_personal=true`. Она кладёт `callback_data = "gen:" + resultId`
  (resultId — случайный короткий id) и хранит текст query в **in-memory map**
  `resultId → queryText` внутри adapter-telegram. При callback достаёт query из map
  и вызывает `HandleCallbackUseCase(queryText, …)`. AD-3 не нарушен: AiRequest
  создаёт impl. AD-9 не нарушен: in-memory map — не персистентность контекста диалогов.
- **Story B** соблюдает AD-8: она ожидает `callback_data` = opaque token, ссылающийся
  на **персистентную** сущность (PendingQuery/AiRequest, созданную ещё на inline_query),
  потому что после рестарта/деплоя (AD-11: deploy с verification, rolling restart)
  in-memory map пуста, а кнопка в уже опубликованном сообщении живёт днями.
  B проектирует `HandleCallbackUseCase(token, inlineMessageId, from)` — другой signature.

**Несовместимость:** два разных inbound-контракта `HandleCallbackUseCase`, два
формата `callback_data`, два места хранения query. Обе stories «зелёные» по AD.
Дополнительно не зафиксировано:

1. **Где живёт query между inline_query и callback.** `callback_data` ≤ 64 байт —
   длинный query туда не помещается; spec §10 требует «Respect Telegram inline
   query length restrictions», но не говорит, кто и где хранит полный текст.
   ER-черновик: `ai_requests.query_text` — но AiRequest по AD-8 создаётся **на
   callback**, т.е. на inline_query хранить query негде, если не ввести сущность.
2. **Кнопка до публикации vs после.** Callback с inline-результата *до* выбора
   результата не имеет `inline_message_id` (сообщение ещё не опубликовано).
   AD-8 говорит «отсутствующий inline_message_id → gracefully», но не фиксирует,
   является ли это нормальным сценарием (кнопка на article) или ошибкой (кнопка
   только на опубликованном сообщении). Story A делает кнопку на article и шлёт
   `answerCallbackQuery` с alert «сначала отправьте результат»; Story B считает
   отсутствие id истёкшим callback и ставит EXPIRED. Разный UX, разные статусы.
3. **Формат токена — чей.** Если формат придумывает adapter-telegram (A), а
   валидацию/разбор — impl (B), возникает скрытый общий контракт через строку,
   который никто не owns. AD-3 «адаптеры передают команды/события» это не запрещает.

**Spec anchors:** §10 (callback validation, idempotent processing, length
restrictions), §14 Phase 2/6; AD-8, AD-3.

**Предлагаемый AD-13 — Контракт корреляции inline_query → callback [NEW]:**

- **Binds:** application-api, adapter-telegram, application-impl, adapter-persistence.
- **Prevents:** два формата callback_data; потерю query при рестарте; расхождение
  inbound-контрактов Phase 2 и Phase 6; adapter-owned бизнес-корреляцию.
- **Rule:** на `inline_query` application-impl создаёт персистентную запись
  корреляции (PendingQuery или AiRequest в статусе CREATED — выбрать один вариант
  и зафиксировать в AD-3) с `query_text`, `telegram_user_id`, `result_id`
  (UUID внутри, наружу — короткий opaque token). `callback_data` — **только**
  opaque token фиксированного формата (≤ 64 байт), формат и генерация — в
  application-impl/api; adapter-telegram передаёт token непрозрачно, не парсит
  и не генерирует. Query между inline_query и callback хранится только в БД
  (TTL configurable), не в памяти адаптера. Кнопка Generate Answer существует
  только на опубликованном inline-сообщении; callback без `inline_message_id` —
  валидационная ошибка (доменный код), не нормальный путь. Контракт
  `HandleCallbackUseCase` фиксируется в application-api до начала Phase 2
  (mock-реализация обязана использовать тот же контракт).

---

## H2. Идемпотентность: update_id не покрывает бизнес-дубли; якорь AiRequest [BLOCKER]

**Пара:** «Persistence story (Phase 3)» vs «Telegram adapter story (Phase 2/6)».

- **Story A** соблюдает AD-3 буквально: `telegram_update_id` — unique constraint
  (ER-черновик: `ai_requests.telegram_update_id UK`), повторный update с тем же
  id не создаёт новый AiRequest. Она считает, что идемпотентность = транспортная.
- **Story B** соблюдает AD-3 и AD-8: пользователь жмёт Generate Answer дважды
  (или публикует один результат в два чата и жмёт в обоих). Telegram доставляет
  **разные** `callback_query` updates с **разными update_id** и разными
  `inline_message_id`. B создаёт по AiRequest на каждый update — формально
  законно: constraint не нарушен, «повторного update с тем же id» не было.

**Несовместимость:** два параллельных GENERATING на одно опубликованное
сообщение → гонка `editMessageText` (last-write-wins, «message is not modified»,
двойной расход токенов и бюджета). Кроме того:

1. **Какой update_id якорит AiRequest?** AiRequest создаётся на callback (AD-8),
   значит `ai_requests.telegram_update_id` = id callback-update. Но тогда
   inline_query-update нигде не дедуплицируется (ledger нет), и повторная
   доставка inline_query после рестарта polling создаёт **новую** корреляционную
   запись с новым token (H1) — старая кнопка и новая кнопка ведут к разным
   записям. Story A заводит отдельный ledger `processed_updates`, Story B — нет;
   обе «соблюдают» AD-3, потому что AD не говорит, где живёт constraint и что
   делать с updates, не создающими AiRequest (inline_query, chosen_inline_result).
2. **Один результат → N публикаций.** `ai_requests.inline_message_id` (ER: один
   varchar) не вмещает N чатов. Story A генерирует один ответ на query и
   редактирует все N сообщений (ей нужна связь 1:N, которой в ER нет); Story B
   генерирует на каждую публикацию (N AiRequest, N расходов). Обе AD-совместимы;
   данные и cost-модель несовместимы.

**Spec anchors:** §10 «Idempotent update processing», «Duplicate update
protection» (§12), §11 unique constraints; AD-3, AD-8, AD-10.

**Предлагаемый AD-14 — Бизнес-идемпотентность и якорь запроса [NEW + tighten AD-3]:**

- **Binds:** adapter-telegram, application-impl, adapter-persistence.
- **Prevents:** двойные генерации на одно сообщение; гонку editMessageText;
  неопределённость «какой update_id хранится»; расхождение 1:1 vs 1:N
  запрос→публикация.
- **Rule:** транспортная идемпотентность — отдельный ledger
  `processed_updates(telegram_update_id BIGINT PK, kind, processed_at)` для
  **всех** типов updates; `ai_requests.telegram_update_id` хранит id
  callback-update, создавшего запрос (зафиксировать в AD-3 явно). Бизнес-ключ:
  **не более одного активного (CREATED/GENERATING) AiRequest на
  `inline_message_id`** — partial unique index в миграции; повторный callback
  по тому же `inline_message_id` идемпотентно возвращает существующий запрос
  (повторный edit допустим только как retry того же результата, не новая
  генерация). Гранулярность генерации — **на публикацию** (один
  inline_message_id = один AiRequest = один расход); это решение фиксируется
  здесь, а не в stories. Повторный inline_query с тем же текстом от того же
  пользователя в окне TTL переиспользует корреляционную запись (H1), а не
  плодит token'ы.

---

## H3. Status machine: граф переходов, терминальность, писатель EXPIRED/FAILED, recovery [BLOCKER]

**Пара:** «Async generation story (Phase 6)» vs «Telegram adapter story (Phase 2)» vs «GigaChat provider story (Phase 4)».

AD-3 фиксирует только список статусов и «меняет только application-impl».
AD-8 говорит «истёкшие callback → статус EXPIRED/FAILED», но не говорит, **кто
и через какой use case** его ставит. Отсюда три легальные несовместимости:

- **Story A (async)** делает watchdog: scheduled job переводит
  CREATED/GENERATING старше TTL в EXPIRED. Одновременно генерация ещё идёт
  (LLM отвечает медленно, но успевает до edit). Watchdog ставит EXPIRED,
  генерация завершается и ставит COMPLETED поверх EXPIRED (или наоборот —
  порядок не определён), editMessageText уходит в «истёкшее» сообщение.
  AD-3 не запрещает: оба писателя — application-impl.
- **Story B (telegram)** при невалидном callback просто делает
  `answerCallbackQuery` и **не создаёт/не меняет** AiRequest (AD-3 запрещает
  адаптеру мутацию; отдельного use case «expire» в контрактах нет —
  `contracts.md` его не lists). Запрос, созданный на inline_query (H1, вариант
  PendingQuery=AiRequest CREATED), навсегда остаётся CREATED. AD-8 формально
  «gracefully» соблюдён (безопасный ответ есть), статус — нет.
- **Story C (persistence/recovery)** при старте приложения видит зависшие
  GENERATING (in-process executor, [ASSUMPTION] в Deferred; деплой убивает
  in-flight). Она их **не трогает** (AD не требует recovery) → сообщения,
  которые никогда не отредактируются, и вечно растущий «active» набор,
  блокирующий бизнес-unique из H2. Либо она их сбрасывает в FAILED — но тогда
  гонка с реально ещё живым исполнителем при graceful shutdown.

Также не зафиксировано: допустимые переходы (CREATED→FAILED напрямую?
GENERATING→CREATED при retry? COMPLETED→FAILED при ошибке edit?), запрет
мутации терминальных статусов, и кто ставит FAILED при ошибке
`editMessageText` (генерация успешна, доставка — нет; это COMPLETED или FAILED?
usage уже потрачен).

**Spec anchors:** §10 (graceful expired/invalid), §14 Phase 6 «timeout and retry
handling», §11 lifecycle; AD-3, AD-8; Deferred «in-process executor + persisted state».

**Предлагаемый AD-15 — AiRequest state machine и единый писатель переходов [NEW + tighten AD-3/AD-8]:**

- **Binds:** application-impl, application-api, adapter-persistence, adapter-telegram.
- **Prevents:** гонку watchdog vs generation; зависшие CREATED/GENERATING;
  перезапись терминальных статусов; «доставка упала — статус неопределён».
- **Rule:** явный граф переходов в domain (`AiRequestStatus` + таблица
  допустимых переходов, enforced в domain-методе, не в SQL):
  `CREATED → GENERATING | EXPIRED | FAILED`,
  `GENERATING → COMPLETED | FAILED | EXPIRED`,
  терминальные (COMPLETED/FAILED/EXPIRED) — immutable. Переход — атомарный
  compare-and-set по текущему статусу (optimistic lock / `UPDATE … WHERE
  status = :expected`); проигравший CAS не редактирует сообщение. EXPIRED
  ставит **только** application-impl: (a) use case валидации callback,
  вызываемый адаптером при невалидном/истёкшем callback (adapter не мутирует
  сам — AD-3), (b) watchdog TTL. Ошибка доставки (editMessageText) после
  успешной генерации = COMPLETED + отдельный `delivery_error_code` (генерация
  и доставка — разные оси; зафиксировать поля в ER). Recovery при старте:
  application-impl переводит осиротевшие GENERATING (heartbeat/lease старше
  TTL) в FAILED с кодом `RESTARTED`; lease обновляется исполнителем — это
  единственный механизм, делающий in-process executor безопасным при деплое.

---

## H4. AiRequestUsage: два владельца по букве AD + UK против multi-call [BLOCKER]

**Пара:** «GigaChat provider story (Phase 4)» vs «Async generation story (Phase 6)» vs «Monitoring story (Phase 7)».

Внутреннее противоречие spine'а: **AD-6** (Binds: adapter-llm) говорит «usage
tokens фиксируются в `AiRequestUsage`», а **AD-3** — «адаптеры передают
команды/события, не мутируют домен». `AiRequestUsage` — доменная сущность
(ER, §11). Буквальное чтение AD-6 разрешает адаптеру писать её (через
репозиторий-порт — это не «lifecycle AiRequest», значит AD-3 формально не запрещает).

- **Story A (Phase 4)** пишет `AiRequestUsage` прямо из `GigaChatProvider`
  после каждого completion (AD-6 велит). `latency_ms` = время HTTP-вызова.
- **Story B (Phase 6)** пишет usage из `LlmResponse.usage` в
  `GenerateAnswerUseCase` после завершения (AD-3: всё пишет impl).
  `latency_ms` = end-to-end включая search.
- **Story C (Phase 7, infrastructure)** считает `estimated_cost` из своей
  таблицы тарифов и апдейтит usage для budget-дашборда.

**Несовместимость:** ER-черновик `AI_REQUEST_USAGE.request_id FK,UK` — одна
строка на запрос. A и B вместе → duplicate key на первом же реальном запросе.
A и C → разные `latency_ms`/`estimated_cost` в одной строке, last-write-wins.
Хуже: **Function Calling / search synthesis (§9) = несколько completions на
один AiRequest** (tool call → search → final). UK на request_id физически
запрещает per-call usage; stories Phase 4 и Phase 5 обязаны разойтись: одна
агрегирует токены в одну строку, другая хочет N строк. Никто не владеет
ценой: `estimated_cost` требует прайс-таблицы, которой нет ни в AD, ни в ER
(`ai_providers.config` jsonb «без секретов» — единственное намёк-место).

**Spec anchors:** §8 «Token usage tracking», §9 (synthesis, function calling),
§10 шаг 7 «Record the result and usage metrics», §12 global LLM budget; AD-6 vs AD-3.

**Предлагаемый AD-16 — Единый владелец usage и гранулярность учёта [NEW + tighten AD-6]:**

- **Binds:** adapter-llm, application-impl, infrastructure, adapter-persistence.
- **Prevents:** двух писателей AiRequestUsage; UK-violation при multi-call;
  расхождение latency/cost; budget, считающий не то.
- **Rule:** adapter-llm **возвращает** usage в `LlmResponse` (per-call) и ничего
  не персистит (устранить двусмысленность AD-6: «фиксируются» = «возвращаются
  ядру для фиксации»). Единственный писатель `AiRequestUsage` —
  application-impl. Гранулярность: **одна строка на LLM-call**
  (`request_id FK`, `call_index`, unique `(request_id, call_index)`; UK на
  request_id убрать из ER), агрегат по запросу — вычисляется. `latency_ms` =
  длительность LLM-call (измеряет adapter, передаёт в response); end-to-end
  latency — отдельное поле/метрика impl. `estimated_cost` считает только
  application-impl по единой прайс-конфигурации (`smartbot.pricing.*`,
  configurable, AD-10); infrastructure читает, не пишет. Budget (H6)
  резервируется/списывается по тем же per-call записям.

---

## H5. User: гонка первой регистрации, владелец lifecycle, статус blocked [HIGH]

**Пара:** «Telegram adapter story (Phase 2)» vs «User management story (Phase 3)» vs «Rate limiting story (Phase 7)».

AD-10: «Автоматическая регистрация при первом обращении». Не сказано, **кто**
регистрирует и что такое «обращение».

- **Story A (Phase 2)** вызывает `RegisterUserUseCase` из адаптера перед каждым
  use case (адаптеру это не запрещено: AD-3 ограничивает только AiRequest).
- **Story B (Phase 3)** делает get-or-create внутри `HandleInlineQueryUseCase` /
  `HandleCallbackUseCase` (impl).
- Оба потока живы одновременно: первый `inline_query` и первый `callback_query`
  (или два параллельных updates после рестарта polling) → два INSERT в
  `users.telegram_user_id UK` → один падает с constraint violation, и этот
  update теряется/ретраится по-разному в A и B.
- **Story C (Phase 7)** проверяет `users.status = 'blocked'` (ER-черновик) в
  rate-limiter'е. Story A/B при каждом обращении делают upsert
  `status = 'active'` («регистрация при первом обращении» трактуется как
  «актуализация при каждом») → **разблокировка заблокированных пользователей**
  каждым новым запросом. Никто не владеет полем status.

**Spec anchors:** §10 «User identification», §12 «Automatic user registration»,
§11 User/UserSettings; AD-10, AD-3 (по аналогии — не распространён на User).

**Предлагаемый AD-17 — Единый владелец User и get-or-create semantics [NEW + tighten AD-10]:**

- **Binds:** application-impl, adapter-telegram, infrastructure, adapter-persistence.
- **Prevents:** гонку регистрации; саморазблокировку; двух владельцев users.status;
  регистрацию в адаптере.
- **Rule:** User создаёт/обновляет **только** application-impl (расширить
  принцип AD-3 на User/UserSettings). Адаптер передаёт `TelegramPrincipal`
  (telegram_user_id, username, first_name, language_code) в каждом use case;
  impl делает атомарный get-or-create (`INSERT … ON CONFLICT (telegram_user_id)
  DO UPDATE` только profile-полей и `last_seen_at`). Поля `status` и
  `history_enabled` upsert **не трогает**; blocked меняет только явный
  admin/use-case путь. Проверка blocked — до создания AiRequest и до списания
  квоты (порядок с H6). Регистрация происходит на первом **любом** update
  (inline_query или callback), идемпотентно.

---

## H6. Лимиты: точка enforcement, учёт отказов, max output tokens в трёх местах [BLOCKER]

**Пара:** «Rate limiting story (Phase 7, infrastructure)» vs «Async generation story (Phase 6, impl)» vs «GigaChat provider story (Phase 4)».

AD-10 перечисляет лимиты и «конфигурируемость», capability map кладёт их в
infrastructure, но **точка применения не зафиксирована**. AD-2 легализует оба
стиля: INFRA→IMPL разрешён (AOP-обёртка use cases), INFRA→API разрешён
(реализация порта `QuotaPort`, вызываемого из impl).

- **Story A (infra)** оборачивает `HandleCallbackUseCase` AOP-интерсептором: отказ
  **до** создания AiRequest. Отказавшие запросы не персистятся, квота не
  расходуется, пользователь видит generic alert.
- **Story B (impl)** проверяет квоту внутри use case **после** создания
  AiRequest: отказ = FAILED c `error_code = RATE_LIMITED`, строка в истории,
  и (по её логике) попытка **потребляет** дневную квоту («запрос был»).
- **Story C (provider)** применяет `max_output_tokens` из
  `ai_providers.config` (jsonb), игнорируя `LlmRequest.maxOutputTokens`
  (эскиз contracts.md), который impl заполняет из `smartbot.limits.*`.
  Два конфига, два значения, приоритет не определён. Global budget: A
  резервирует до вызова, C списывает по факту usage → двойной учёт или bypass.

**Несовместимость:** разная персистентность отказов (дашборды Phase 7 считают
не то), разная семантика квот (потребляет ли отказ), два источника max tokens,
две точки budget. Всё «конфигурируемо» по AD-10 — и всё несовместимо.
Дополнительно: **request timeout** (AD-10) vs LLM timeout (§8) vs HTTP timeout —
иерархия не задана; если request timeout < LLM timeout, watchdog (H3) убивает
запросы, которые provider ещё легально ретраит (H9).

**Spec anchors:** §12 целиком, §8 timeouts, §10; AD-10, AD-2, AD-6.

**Предлагаемый AD-18 — Единая точка enforcement и семантика учёта лимитов [NEW + tighten AD-10]:**

- **Binds:** infrastructure, application-impl, adapter-llm, application-api.
- **Prevents:** двойной enforcement; расхождение учёта квот/бюджета; два
  источника max tokens; инверсию timeout-иерархии.
- **Rule:** все per-user проверки (rate limit, quota, blocked) и global budget
  reserve выполняются **в application-impl** в фиксированном порядке до
  перевода AiRequest в GENERATING, через порты (`RateLimitPort`, `QuotaPort`,
  `BudgetPort` в application-api; реализация — infrastructure). Infrastructure
  не оборачивает use cases AOP'ом. Отказ лимита = AiRequest FAILED с
  доменным кодом (H8), attempt **не потребляет** квоту генерации, но
  учитывается в rate-limit окне (зафиксировать обе семантики явно). Budget:
  reserve до call → settle по per-call usage (H4) → release остатка; один
  владелец счёта — impl. `max_output_tokens`: единственное значение —
  `LlmRequest.maxOutputTokens`, вычисляется impl как
  `min(smartbot.limits.max-output-tokens, provider.config.maxOutputTokens?)`;
  provider обязан передать его в API и не превышать; provider config не
  содержит конкурирующего лимита. Иерархия timeout:
  `request TTL > generation timeout > LLM call timeout (incl. retries) > HTTP timeout`,
  значения configurable, соотношение enforced тестом.

---

## H7. Length budgets: query validation, 4096 символов, источники vs обрезка [HIGH]

**Пара:** «Async generation story (Phase 6)» vs «Telegram adapter story (Phase 2)» vs «Search synthesis story (Phase 5)».

Spec §10: «Respect Telegram inline query length restrictions»;
telegram-integration.md: «Лимиты длины inline query и результата — соблюдать».
Ни один AD не фиксирует бюджеты и владельца обрезки.

- **Story A (async)** обрезает `result_text` до 4096 символов **перед**
  editMessageText,.sources footer (AD-7: «Исходные URL сохраняются в финальном
  ответе») appending после обрезки — или обрезает и footer, теряя URL
  (формально AD-7 нарушен? Нет: A считает, что «финальный ответ» — это то,
  что ушло в Telegram, и кладёт URL в начало). Хранит в БД полную версию.
- **Story B (telegram)** считает, что обрезка — дело ядра, и сама режет
  всё, что длиннее лимита, «по-умному» (по границе абзаца), экранирует
  MarkdownV2. A уже обрезала → двойная обрезка, разные `result_text` в БД
  и в сообщении; parse_mode у A — HTML, у B — MarkdownV2 → edit падает на
  entities, которые B не экранировала (или наоборот).
- **Story C (search)** кладёт в prompt snippet'ы произвольной длины
  (SearchResult.snippet без cap, AD-7 поля фиксирует, размеры — нет) →
  prompt + max_output_tokens выбивают контекст модели; «обрезка ответа»
  начинается ещё до генерации, и никто не владеет prompt budget.
- Query validation: A валидирует длину query в адаптере (отклоняет > лимита
  Telegram), B — в impl (обрезает до лимита и генерирует по обрезанному).
  В БД попадает разный `query_text` (история ≠ то, что видел LLM).

**Spec anchors:** §10 length restrictions, §9 snippet/preserve URLs, §12;
AD-7, AD-8, AD-10.

**Предлагаемый AD-19 — Бюджеты длины и единственный владелец форматирования [NEW]:**

- **Binds:** adapter-telegram, application-impl, adapter-search, application-api.
- **Prevents:** двойную обрезку; потерю источников при обрезке; parse_mode-конфликт;
  неопределённость «что хранится vs что отправлено»; переполнение prompt.
- **Rule:** бюджет Telegram (4096 символов текста сообщения, лимиты длины
  query/callback_data) — знание **adapter-telegram**; он экспортирует
  константы/порт `MessageLimits`, impl использует их при формировании
  финального текста. Финальный текст для Telegram (включая обрезку и
  обязательный sources-блок с оригинальными URL — AD-7 приоритетнее
  полноты ответа: URL не обрезаются никогда, обрезается тело) формирует
  **application-impl** целиком; adapter отправляет as-is, не режет и не
  форматирует. `parse_mode` — один на проект (зафиксировать: HTML или
  MarkdownV2), экранирование — в adapter-telegram по единому правилу.
  В БД хранятся: полный `result_text` (до обрезки) и отправленная версия
  (или флаг truncation) — зафиксировать поля. Query: валидация длины —
  adapter (отказ с доменным кодом, без обрезки); в БД и в LLM уходит
  идентичный текст. Snippet cap и prompt budget — configurable, владелец —
  impl (SearchResult.cap применяется до сборки prompt).

---

## H8. Error codes: каталог не зафиксирован; секреты через exception messages [HIGH]

**Пара:** «GigaChat provider story (Phase 4)» vs «Telegram error handling story (Phase 2/6)» vs «Search provider story (Phase 5)».

Convention «адаптеры маппят внешние ошибки в доменные коды; наружу —
безопасные сообщения» есть, но **каталога кодов нет** (contracts.md: «Error
mapping адаптеров → доменные коды ошибок (Phase 4)» — отложено).

- **Story A (llm)** бросает `LlmException("GigaChat returned 401: " + responseBody)`
  — «доменный код» она понимает как строку-префикс `GIGACHAT_AUTH`.
  Response body OAuth-эндпоинта может содержать эхо параметров запроса;
  debug-лог WebClient печатает заголовки (Authorization Key). AD-4 формально
  соблюдён: «логи — redaction чувствительных полей» — A настроила redaction
  полей `authorization`, `token` в своих логах, но exception message — не лог.
- **Story B (telegram)** рендерит пользователю `exception.getMessage()`
  «безопасного доменного исключения» (оно же доменное!) → внешний payload
  уходит в публичный чат. AD-4 «только безопасные сообщения» B считает
  выполненным по построению.
- **Story C (search)** изобретает свои коды `SEARCH_ERR_1…`, persistence
  пишет `error_code varchar` чем попало; B маппит коды в user-messages своим
  switch'ем → неизвестные коды = generic «ошибка» (ок) или fallthrough с
  сырым текстом (не ок). Три владельца одного каталога.

**Spec anchors:** §8 «Error mapping», §10 «Do not expose AI provider
credentials through Telegram», §12 «Safe error responses», «Sensitive data
redaction»; AD-4, AD-6, Conventions/Errors.

**Предлагаемый AD-20 — Доменный каталог ошибок и безопасные exception'ы [NEW + tighten AD-4]:**

- **Binds:** smartbot-domain, application-api, все адаптеры, application-impl.
- **Prevents:** утечку внешних payload/секретов через exception messages в
  Telegram и логи; три каталога кодов; fallthrough-рендеринг.
- **Rule:** единый enum `ErrorCode` в smartbot-domain (каталог фиксируется в
  spine/companion до Phase 2: AUTH, RATE_LIMITED, QUOTA_EXCEEDED,
  BUDGET_EXCEEDED, TIMEOUT, PROVIDER_UNAVAILABLE, SEARCH_UNAVAILABLE,
  INVALID_CALLBACK, EXPIRED, MESSAGE_TOO_LONG?, INTERNAL, …). Доменные
  исключения несут **только** `ErrorCode` + типизированные безопасные
  параметры; сырые внешние тела/заголовки **не пересекают порт** (adapter
  логирует их сам, с redaction, и наружу отдаёт только код). Рендеринг
  user-message — единственная таблица `ErrorCode → безопасный текст` в
  application-impl/api; adapter-telegram не содержит собственного маппинга
  и никогда не использует `getMessage()` для пользователя. Неизвестный код
  невозможен (enum). Тест-страж: ArchUnit/IT проверяет, что ни один
  user-facing путь не принимает String message из адаптера.

---

## H9. Retry и timeout: мультипликативные ретраи, дубль in-flight генерации [HIGH]

**Пара:** «GigaChat provider story (Phase 4)» vs «Async generation story (Phase 6)».

Spec §8 требует «Retry policies» у provider; Phase 6 — «timeout and retry
handling» у async. AD-6/AD-8 не разводят владение.

- **Story A (provider)** ретраит 429/5xx внутри `complete()` (3 попытки,
  backoff). Каждый OAuth-запрос — новый RqUID (AD-6 соблюдён).
- **Story B (async)** ретраит шаг генерации целиком при любом сбое
  (2 попытки). Итого 6 LLM-вызовов на запрос, бюджет (H6) резервировал один.
  Хуже: B ретраит по **timeout**, а A в это время ещё ждёт ответ (HTTP
  timeout A > timeout B, иерархия не задана — H6) → два in-flight completion,
  два usage (H4), два edit'а. RqUID уникален per OAuth request, но
  идемпотентности completion нет — GigaChat посчитает оба.
- Token refresh: A обновляет токен по expiration «thread-safe» (AD-6) —
  double-checked locking в памяти процесса. Deferred «Redis: token cache»
  остаётся открытым: story «Redis token cache» (инфраструктура) легально
  строит второй кэш поверх → stale token после refresh другим потоком/
  инстансом, refresh storm (N параллельных 401 → N OAuth-запросов).
  Single-flight не зафиксирован ни для процесса, ни для кластера; запас на
  clock skew не задан.

**Spec anchors:** §8 (retry policies, thread-safe refresh, RqUID), §14 Phase 6;
AD-6, AD-8, AD-10, Deferred (Redis).

**Предлагаемый AD-21 — Владение retry, single-flight генерации и token refresh [NEW + tighten AD-6]:**

- **Binds:** adapter-llm, application-impl, infrastructure.
- **Prevents:** мультипликативные ретраи; дубль in-flight completion;
  refresh storm; stale token при появлении Redis-кэша.
- **Rule:** transport-retry (429/5xx/сетевые, с backoff и cap) — **только**
  adapter-llm, внутри одного `complete()`, в пределах LLM call timeout (H6).
  application-impl **не ретраит** LLM-call; бизнес-retry допустим только как
  явный новый AiRequest по новому callback (H2/H14). Один AiRequest = один
  in-flight исполнитель: lease (H3/H15) гарантирует, что watchdog/recovery не
  запустит вторую генерацию параллельно живой. Token refresh: single-flight
  на scope (один refresh в полёт, остальные ждут результат), обновление по
  expiration с configurable safety margin; кэш токена — один на приложение
  (in-process; при выборе Redis — тот же single-flight через атомарную
  операцию, решение ADR до внедрения Redis, не story). Каждый retry
  completion не требует нового RqUID (RqUID — только OAuth), но каждый
  OAuth-запрос, включая refresh, — новый UUID (AD-6).

---

## H10. SearchResult: форма не канонизирована; citations в двух форматах [MEDIUM→HIGH]

**Пара:** «External search provider story (Phase 5/F)» vs «Function calling synthesis story (Phase 5)» vs «Persistence story (Phase 3)».

AD-7 фиксирует **имена полей**, но не типы, ограничения и сериализацию.

- **Story A (external)** возвращает `sourceMetadata: Map<String, Object>`
  (вложенные объекты от провайдера), `publicationDate: String` («2026-11-20»),
  snippet до 2000 символов, url с tracking-параметрами, дубликаты по url.
- **Story B (synthesis)** сериализует SearchResult в JSON для tool-response
  GigaChat, ожидает плоские строки в metadata и ISO-даты; цитирует источники
  inline-ссылками `[текст](url)` в Markdown. A цитирует нумерованным
  footer'ом «Источники: 1. …». Финальные ответы двух веток AUTO-режима
  выглядят по-разному; Telegram parse (H7) ломается на одном из форматов.
- **Story C (persistence)** пишет `ai_requests_searches.normalized_results`
  jsonb «как пришло» → две несовместимые схемы в одной колонке; аналитика
  Phase 7 не может прочитать обе.

**Spec anchors:** §9 (normalized results, preserve URLs, avoid multiple
providers), §11; AD-7.

**Предлагаемый AD-22 — Канонический SearchResult и единый рендеринг цитат [tighten AD-7]:**

- **Binds:** smartbot-domain, application-api, adapter-search, application-impl, adapter-persistence.
- **Prevents:** две формы нормализованных результатов; нечитаемый jsonb;
  два формата цитирования; раздувание prompt.
- **Rule:** `SearchResult` — тип в smartbot-domain с фиксированными типами:
  `title: String`, `url: String` (нормализованный http(s), без tracking —
  правило нормализации в AD/companion), `snippet: String` (cap configurable),
  `publicationDate: Instant?`, `sourceMetadata: Map<String, String>` (плоский).
  Нормализацию выполняет adapter-search **до** возврата; impl валидирует.
  Дедупликация по url — impl. В jsonb хранится только каноническая форма.
  Рендеринг цитат в финальном ответе — единственный, в application-impl
  (формат зафиксировать: нумерованный sources-блок), adapter не переформатирует.
  «Не более одного необходимого провайдера» (AD-7): выбор провайдера в
  AUTO — детерминированное правило в impl (приоритет/config), не эвристика
  адаптера.

---

## H11. Transport seam: offset ack, единственный poller, edit-порт и его ошибки [HIGH]

**Пара:** «Telegram transport story (Phase 2, polling)» vs «Async delivery story (Phase 6)» vs «Deployment story (Phase 7, DEV+PROD)».

- **Story A (transport)** подтверждает offset getUpdates **до** обработки
  (at-most-once): быстрее, не блокирует polling. AD-3 «идемпотентно» соблюдён
  vacuously. Story B подтверждает **после** (at-least-once) и полагается на
  ledger (H2). При краше A теряет updates молча — и её «идемпотентность»
  никогда не тестируется, пока B не включает ledger.
- **Edit seam:** AD-8 требует `editMessageText` после async-генерации, но
  outbound-порта для этого **нет в контрактах** (contracts.md lists только
  LlmProvider/SearchProvider/repositories/use cases). Story A (Phase 6)
  заводит `MessageEditorPort` в api, реализует adapter-telegram. Story B
  (Phase 2) делает edit внутри своего inbound-обработчика через внутренний
  сервис адаптера, ожидая, что impl вернёт «готовый текст» синхронно.
  AD-1/2 оба варианта не запрещают (adapter реализует порт api — законно;
  adapter не вызывает impl — законно). Wiring не сходится.
- **Ошибки edit:** «message is not modified» (идемпотентный повтор),
  429 `retry_after`, «message to edit not found» (истёкшее/удалённое
  сообщение). Кто обрабатывает: A (impl) ретраит edit сам → конфликт с
  transport-retry адаптера (двойной retry, H9-аналог); B (adapter) ретраит
  сам и возвращает impl «успех» после первой попытки → impl ставит
  COMPLETED/delivery_error (H3) неверно.
- **Два poller'а:** Deferred «DEV/PROD топология» открыт; один bot token на
  DEV и PROD (обычная практика на старте) → два getUpdates = 409 Conflict,
  updates теряются у обоих. Ни один AD не запрещает два poller'а на token;
  webhook `secret_token` есть только в черновике docs, не в AD (spec §12
  требует webhook authentication — spine это не bind'ит).

**Spec anchors:** §4.3 (getUpdates/webhook не одновременно), §10, §12
(webhook authentication), §14 Phase 2/6/7; AD-8, AD-3, AD-5, Deferred.

**Предлагаемый AD-23 — Транспортный контракт Telegram и порт доставки [NEW]:**

- **Binds:** adapter-telegram, application-api, application-impl, deployment.
- **Prevents:** потерю updates; два poller'а на token; отсутствие edit-порта;
  двойной retry edit'а; webhook без аутентификации.
- **Rule:** семантика доставки — **at-least-once**: offset подтверждается
  только после персистентной обработки (ledger H2); graceful shutdown
  дожидается in-flight или оставляет их recovery'ю (H3). Один bot token =
  ровно один активный poller (или webhook) во всех окружениях; DEV/PROD с
  общим token запрещены без отдельного bot token (фиксировать в deployment
  AD/ADR). Outbound-порт `InlineMessageEditor` (editMessageText по
  inline_message_id) — в application-api, реализация adapter-telegram;
  transport-retry (429 retry_after, сетевые) — внутри адаптера; «message is
  not modified» = успех; «message not found»/истёкший = доменный код
  EXPIRED/DELIVERY_FAILED (H8), impl решает статус доставки (H3).
  Webhook (если выбран ADR-0002): обязательная проверка `secret_token` —
  часть AD, не docs. answerCallbackQuery отправляется **немедленно** при
  валидном callback (до генерации), чтобы не ловить client-side loading
  timeout; порядок фиксируется здесь.

---

## H12. Кто мутирует UserSettings и provider selection: AUTO vs preferred_provider [MEDIUM]

**Пара:** «User settings story (Phase 3)» vs «Search AUTO story (Phase 5)» vs «Provider config story (Phase 4)».

ER-черновик: `user_settings.search_mode`, `preferred_provider_id`;
`ai_providers.enabled`, `default_model`. AD-7: режимы конфигурируемы;
AD-6: модели проверяются через API, не хардкодятся.

- **Story A (settings)** трактует `search_mode` как per-user настройку с
  дефолтом из `smartbot.search.default-mode`; меняет её use case'ом настроек.
- **Story B (AUTO)** трактует режим как **глобальный** конфиг провайдера
  (`search_providers.config`) и игнорирует user_settings (AD-7 говорит
  «конфигурируемы», не говорит «кем»). Пользователь, выбравший NONE,
  получает EXTERNAL-синтез с источниками — приватность/ожидания нарушены,
  AD формально не нарушен.
- **Story C (provider)** при `enabled=false` у единственного провайдера
  падает в INTERNAL; Story A ожидает graceful FAILED с кодом
  PROVIDER_UNAVAILABLE и fallback на default_model из БД vs
  `smartbot.llm.model` из env — два источника истины о модели (AD-6
  «список моделей проверяется через API» — где хранится проверенный список:
  `ai_providers.config`? env? кэш в памяти?).

**Spec anchors:** §9 (modes), §8 (model configuration, verified models),
§11 (UserSettings, AiProvider); AD-6, AD-7, AD-9, AD-10.

**Предлагаемый AD-24 — Иерархия конфигурации: user → provider → global [NEW]:**

- **Binds:** application-impl, adapter-persistence, adapter-llm, adapter-search.
- **Prevents:** два источника истины для search_mode/model; игнорирование
  пользовательских настроек; неопределённость при disabled provider.
- **Rule:** эффективная конфигурация запроса вычисляется **только** в
  application-impl по фиксированному приоритету:
  `UserSettings (если задано) → AiProvider/SearchProvider config (БД) →
  smartbot.* defaults (env)`. Адаптеры получают уже эффективные значения в
  запросе (LlmRequest/SearchQuery) и не читают user_settings/global config
  сами. Проверенный список моделей — в `ai_providers.config` (обновляется
  явным use case/job после верификации через API, AD-6); `default_model`
  обязан входить в проверенный список (constraint тестом). Disabled
  provider → FAILED `PROVIDER_UNAVAILABLE` (H8), без INTERNAL и без
  молчаливого fallback на другой provider (fallback — только явное правило
  в AD, если понадобится).

---

## Внутренние напряжения spine'а (сводка)

| # | Напряжение | Где разрешено |
|---|---|---|
| T1 | AD-6 «usage фиксируются в AiRequestUsage» (Binds adapter-llm) vs AD-3 «адаптеры не мутируют домен» | H4 / AD-16 |
| T2 | AD-8 «статус EXPIRED/FAILED» при невалидном callback vs AD-3 «меняет только impl» — use case для этого не существует | H3 / AD-15 |
| T3 | AD-3 unique `telegram_update_id` как единственная идемпотентность vs AD-8 (AiRequest создаётся на callback, inline_query не якорится) | H2 / AD-14 |
| T4 | AD-2 INFRA→IMPL легализует AOP-enforcement лимитов поверх impl, capability map кладёт лимиты в infrastructure, AD-10 не фиксирует точку | H6 / AD-18 |
| T5 | AD-7 «URL сохраняются в финальном ответе» vs лимит 4096 (§10) — приоритет не задан | H7 / AD-19 |
| T6 | Deferred «in-process executor + persisted state» без invariant recovery/lease | H3 / AD-15 |
| T7 | Conventions «единый error shape» без каталога кодов; contracts.md откладывает маппинг в Phase 4, а safe-messages нужны в Phase 2 | H8 / AD-20 |

## Приоритеты и порядок закрытия

| Hole | Severity | Новый/уточнённый AD | Закрывать до |
|---|---|---|---|
| H1 callback correlation | BLOCKER | AD-13 | декомпозиции Phase 2/6 epics |
| H2 бизнес-идемпотентность | BLOCKER | AD-14 (+AD-3) | Phase 3 persistence story |
| H3 state machine/recovery | BLOCKER | AD-15 (+AD-3/8) | Phase 3/6 |
| H4 usage ownership | BLOCKER | AD-16 (+AD-6) | Phase 4 (ER-миграция UK!) |
| H6 enforcement лимитов | BLOCKER | AD-18 (+AD-10) | Phase 7, но контракт портов — до Phase 6 |
| H5 User registration | HIGH | AD-17 (+AD-10) | Phase 3 |
| H7 length budgets | HIGH | AD-19 | Phase 2 (mock) / Phase 6 |
| H8 error catalog/secrets | HIGH | AD-20 (+AD-4) | Phase 2 (первый user-facing error) |
| H9 retry/single-flight | HIGH | AD-21 (+AD-6) | Phase 4/6 |
| H11 transport/edit seam | HIGH | AD-23 | Phase 2 |
| H10 SearchResult shape | MEDIUM→HIGH | AD-22 (+AD-7) | Phase 5 (jsonb-схема — Phase 3) |
| H12 config hierarchy | MEDIUM | AD-24 | Phase 3/4 |

Замечание по миграциям: H2 (partial unique index), H4 (снятие UK,
call_index), H15 (lease/heartbeat поля), H19 (truncation-поля) меняют схему
из черновика ER. Поскольку AD-9 forward-only Flyway, дешевле зафиксировать
форму **до** первой миграции Phase 3, чем писать corrective-миграции.

## Вердикт

**REVISE.** Spine корректно фиксирует структуру (модули, границы, ID,
секреты-в-хранении, сеть, сборку) — атаки на AD-1/2/4/5/9/11/12 не найдены.
Но поведенческие швы между epics (correlation, идемпотентность, state
machine, usage, лимиты, ошибки, длины, transport) не зафиксированы: найдено
**12 пар AD-совместимых, но несовместимых между собой work units**, включая
одно прямое внутреннее противоречие (AD-6 vs AD-3 по AiRequestUsage).
Рекомендация: принять AD-13…AD-24 (или эквивалентные уточнения AD-3/4/6/7/8/10)
до декомпозиции на epics/stories; минимум — пять BLOCKER (H1, H2, H3, H4, H6)
плюс H8 (безопасность) до начала Phase 2. Spine не модифицирован этим review.
