# Proxy upstream handoff: D9/D10 — 14.09.2026

Продовження [D8 / DLNA HLS ownership](<C:/UA CAST/docs/DLNA_OWNERSHIP_FIX_2026-09-14.md>). Цей етап перевіряє прямий TS, wrapper, звичайний HLS manifest і раннє звільнення remux. Попередні незакомічені зміни збережено; версія, CI, Windows TCP settings і встановлена програма на телефоні не змінювалися. Реліз не публікувався.

## D9 — P1: звичайні upstream Calls не мали власника каналу

**Доказ до виправлення:** `ProxyServer` тримав один набір `upstreamCalls`, який очищався тільки при `stop()`. `registerPlaylist` уже зупиняв flattened HLS після D8, але не прямий passthrough чи другий Call, відкритий після wrapper. Перевірка допуску ordinary Call бачила лише running/accepting, не channel lease.

Шлях: `DlnaSessionRepository.setActiveChannel` → повторне використання proxy/token → `registerPlaylist(B)` → старий `servePassthrough(A)` продовжує копіювати тіло. Якщо A ще чекає headers, його Call також продовжувався. Запізнілий wrapper міг відкрити внутрішній origin уже після A → B → A.

Окремий прояв тієї ж втраченої належності: звичайний rewritten manifest перевіряв лише присутність parent ID у поточному graph. При A → B → A той самий parent ID знову актуальний, але старе тіло A належить іншому поколінню. Воно могло публікувати старі segment URLs у новий graph. Перевірки тільки перед читанням недостатньо: перемикання може статися під час читання тіла.

**Наслідок:** зайві origin connections і трафік, ризик відмов нового каналу при provider connection cap, застарілий manifest/state. Нескасовані Calls, зайвий inner fetch та прийняття старого manifest підтверджені тестами. Відмову конкретного провайдера чи телевізора тут не вимірювали.

**Виправлення:**

- [ProxyServer.registerPlaylist](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:199>) змінює owner і скасовує старі producer Calls під одним `producerLock`, спільним із допуском upstream та flattened replay. Окремі locks для цих операцій більше не залишають вікна між cancellation snapshot і новим admission.
- Кожен запит каналу, включно з wrapper inner Call, несе початковий lease. Неактуальний URL відхиляється до нового origin fetch. Повторна реєстрація незміненого root не скасовує його потік.
- Після sniff/callback є повторна перевірка: `Call.cancel()` не гарантує exception, якщо потрібні bytes уже в buffer.
- [Публікація manifest](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyResourceRegistry.kt:90>) звіряє початковий lease атомарно з rewrite під `remuxLock`. Запізніле читання після A → B → A не змінює graph.
- Скінченні MEDIA-запити, обіцяні попереднім HLS manifest, не скасовуються при зміні каналу. Їх і надалі скасовує повний `stop()`. Це зберігає handoff, не відкриваючи старому root права запустити новий live producer.

## D10 — P1: remux звільняв попередній origin надто пізно

**Доказ до виправлення:** `ProxyResourceRegistry.registerPlaylist(B)` змінював root, але залишав reader A активним. `beginDraining(A)` виконувався лише всередині `startRemuxSession(B)`, тобто після отримання відповіді B. Провайдер із лімітом одного потоку може відмовити B саме тому, що A ще тримає з'єднання; тоді код навіть не досягав точки закриття A.

**Виправлення:** [реєстрація нового root](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyResourceRegistry.kt:59>) одразу зупиняє старий reader і переводить його готові сегменти в draining. Немає очікування reader join на main thread. Локальний response закривається до наступного origin fetch; це не обіцянка, що віддалений провайдер миттєво обробить TCP close.

При поверненні A → B → A новий запит A не отримує заморожений draining-playlist замість свіжого producer. Старі готові segment bytes залишаються доступними на час handoff. Незмінний root не перезапускається.

Lock order: `producerLock` → registry `remuxLock` → manifest lock. Rewrite під registry lock не захоплює `producerLock`; URL builder читає тільки поточні поля/port. Нового зворотного порядку locks не додано.

## Тести до і після

Початковий негативний прогін до production-змін: **20 tests, 8 failures** — 12 нових сценаріїв і 8 попередніх remux регресій. Два з failures були timeout очікуваної 503, оскільки баг відкривав навмисно нескінченний синтетичний TS. Це контрольований неправильний маршрут, а не залишковий Windows tail-read O1.

Фінально додано **14 нових tests**:

- [ProxyUpstreamOwnershipTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyUpstreamOwnershipTest.kt>) — 11: direct/wrapper cancellation, pending headers, незмінний root, рання відмова старому URL, late wrapper, stale manifest до та після routing, два сценарії finite segment handoff, повний stop після handoff.
- [ProxyRemuxOwnershipTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyRemuxOwnershipTest.kt>) — 3 нових: раннє закриття попереднього origin, повернення до draining A, збереження незміненого producer. Разом у класі тепер 11 tests.

Наявні fixtures актуалізовано без послаблення перевірок:

- `ProxyServerTest` тепер реєструє root перед прямим викликом внутрішнього serving helper, як це робить production path.
- `ProxyFlattenedOwnershipTest` очікує **0** origin fetches для нового запиту старого URL, замість колишнього одного початкового fetch.
- Wrapper-сценарій `ProxyRemuxRequestRaceTest` перевіряє, що внутрішній origin взагалі не відкривався. Для вже отриманих raw responses перевірка закриття залишилася.

Точковий набір: **61/61 PASS**. Detekt вказував тільки на довгі рядки нових tests; їх перенесено, правила/baseline не змінювалися.

### Повна JVM-матриця

JDK 21.0.11, усі test tasks примусово виконані заново, без exclusions/retries чи змін timeout:

| Набір | Виконань | Failures |
|---|---:|---:|
| core | 119 | 0 |
| Debug | 2120 | 2 |
| Release | 1905 | 1 |
| Play | 1905 | 0 |

Разом **6049 виконань, 3 failures, 0 errors**. Variant-прогони містять повтори тих самих tests; це не 6049 унікальних tests. Усі нові 14 регресій PASS у Debug/Release/Play.

Залишкові failures: `ProxyLargeResponseTest` у Debug/Release та `ProxyManifestLeaseTest` у Debug — відомі Windows TCP timeouts. Play у цьому прогоні повністю зелений; нестабільність O1 не дозволяє вважати це його виправленням. Gradle загалом FAILED через два test tasks.

`lintDebug`, `lintRelease`, `lintPlay`: 0 errors, 0 warnings. `app:detekt`, `core:detekt`, `assembleDebug`, `assembleDebugAndroidTest`: PASS.

XML збережено під `build/proxy-upstream-20260914/{before,targeted,full-Debug,full-Release,full-Play}`; це generated artifacts, які може видалити `clean`.

### Mi A2 / Android API 30

**62/62 PASS, 17,105 s** через Android ART: попередній набір 48 плюс нові 14. Включено справжній HTTP proxy, великі відповіді, 1000 segment URLs, renderer profile, remux/flattened/direct/wrapper lifecycle. Origin синтетичний.

Runner: `app_process /system/bin org.junit.runner.JUnitCore`, production-класи зі свіжого debug APK, тимчасовий dex tests. Це не UI чи Instrumentation run і не DLNA handshake зі справжнім телевізором.

Початково до dex планувалося додати також 13 `ProxyServerTest`, але D8 відхилив JVM-назву методу з апострофом. Цей JVM-only клас не входив до Android-прогону; усі його 13 tests виконані в повній JVM-матриці. Жоден нормальний Gradle/CI test через це не вимкнено. Android-набір скомпільовано на R8 9.1.29.

SHA-256 APK: `3A743BEFC7AF1DED845525F4B65EAA31AAD5660F0EE0A543CEB21B3E02A86B80`.

APK не встановлювався. Три тимчасові файли та порожню директорію `/data/local/tmp/uacast-proxy-upstream-20260914` прибрано з Mi A2; оригінали APK/dex залишилися у workspace. Дані встановленого застосунку не змінювалися.

## Межі висновку

D9/D10 виправлено та перевірено на JVM і Mi A2. O1 / Windows TCP лишається незакритим; нових network workarounds у production не додано. Зв'язок цих дефектів із конкретним Hisense VIDAA `Error de red` не доведений: потрібен повтор на телевізорі. Цей етап не перевіряв Google Play, UI, реального провайдера з connection cap чи повний Chromecast/DLNA handshake. Публікації або загального вердикту «все виправлено» не було.

Наступний етап: [помилки та зайве читання під час визначення формату потоку](PROXY_PROBE_FIXES_2026-09-14.md).
