# Виправлення D6/D7 — 14 вересня 2026

Наступний етап: [D8 — DLNA flattened ownership, 48 Android tests і уточнення Windows TCP](<C:/UA CAST/docs/DLNA_OWNERSHIP_FIX_2026-09-14.md>). Нижче збережено результати D6/D7 на момент їх перевірки.

Цей звіт продовжує [дебагінг proxy](<C:/UA CAST/docs/DEEP_DEBUG_FOLLOWUP_2026-09-14.md>). Зміни локальні; реліз, versionCode і системні параметри Windows не змінювалися.

## D6: запізнілий upstream не отримує active remux нового каналу

Додано [RemuxRequestLease](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/RemuxRequestLease.kt>): незмінну ідентичність покоління вибраного root-каналу. Порівнюється посилання, не URL/token, тому A → B → A та stop/start з тим самим token не відновлюють права старого запиту. Повторна реєстрація незміненого root без переходу зберігає ідемпотентність і повторне використання producer.

[ProxyServer](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt>) захоплює lease **перед upstream fetch/sniff/callback** і передає його далі, зокрема через проміжний wrapper playlist до другого raw-TS fetch. Після повернення callback lease не перечитується.

[ProxyResourceRegistry](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyResourceRegistry.kt>) під одним `remuxLock` виконує:

- зміну root-власника;
- інвалідацію при clear/open session;
- перевірку належності запиту й публікацію нового active remux.

Неактуальний/null lease не запускає reader; response закривається. Коли клієнтський socket ще живий, запит завершується HTTP 503. Готові сегменти попереднього producer залишаються доступними через draining, але старий запит не може повернути йому active-власність. Підплейлисти актуального manifest graph також отримують lease свого root; вони не відключені новим захистом.

Низькорівневу реєстрацію resource зроблено private: зовнішній caller не може зареєструвати новий root в обхід налаштування власника.

## D7: атомарний per-IP quota

У [ProxyHttpServer](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyHttpServer.kt>) lookup, перевірка cap і increment об'єднані в один `ConcurrentHashMap.compute` для IP. Release/remove працює через атомарний `computeIfPresent` того самого ключа. Вікно, в якому CAS збільшував уже видалений із map counter, усунуто; ідентичність lease для старих server generations збережена.

У тесті відтворення незмінне очікування: **не більше 8 leases**. Ін'єкція interleaving адаптована до атомарної операції: на старій реалізації release потрапляє між lookup і CAS, на новій — перед неподільним compute. Це не вимкнення assertion: стара реалізація видає 9, нова — 8.

## Регресійні перевірки

У штатний `app/src/test` додано 15 tests:

- [ProxyRemuxOwnershipTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyRemuxOwnershipTest.kt>) — 8: A → B, A → B → A, restart, null/stale lease, idempotent registration, draining, nested playlist, закриття відхиленого response.
- [ProxyRemuxRequestRaceTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyRemuxRequestRaceTest.kt>) — 6 HTTP-сценаріїв: channel switch, restart, той самий token і канал після restart, A → B → A, wrapper unwrap, stop без restart. Використовуються справжні ProxyServer/registry/remux і синтетичний origin.
- [ProxyIpLeaseRaceTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyIpLeaseRaceTest.kt>) — race останнього release проти нового admission.

Перший точковий gate: **20/20 PASS** — 15 нових, 4 початкові діагностичні proxy tests і наявний duplicate-remux test. Detekt PASS, baseline не змінювався. XML цього прогону збережено під `build/proxy-race-fixes-20260914/targeted`.

Shell checks: baseline tests 8/8, documentation tests 5/5, порожній baseline, architecture boundaries, documentation references і `git diff --check` — PASS.

Повна не фільтрована JVM-матриця на стандартному JDK 21.0.11 (усі test tasks примусово виконані заново):

| Набір | Виконано | Падінь |
|---|---:|---:|
| core | 119 | 0 |
| Debug | 2100 | 2 |
| Release | 1885 | 1 |
| Play | 1885 | 1 |

Разом **5989 виконань, 4 failures**, не 5989 унікальних тестів. Усі 15 нових регресій PASS у кожному з трьох app variants. `ProxyLargeResponseTest` отримав Windows TCP timeout у всіх трьох; HTTP-сценарій `ProxyManifestLeaseTest` — у Debug. Нових функціональних failures немає, але загальний Windows gate залишається червоним. XML збережено під `build/proxy-race-fixes-20260914/full-{Debug,Release,Play}`.

`lintDebug`, `lintRelease`, `lintPlay`: 0 errors, 0 warnings, 3 наявні hints щодо target SDK/оновлень залежностей. `app:detekt`, `core:detekt`, `assembleDebug`, `assembleDebugAndroidTest`: PASS. Команда Gradle завершилася з помилкою саме через чотири test failures, а не через складання чи статичні перевірки.

## Перевірка на Mi A2

Пристрій Mi A2, Android API 30. Встановлений застосунок, його дані й UI не змінювалися. Свіжий debug APK використано як classpath разом з JUnit і dex нових тестів у власній тимчасовій директорії.

- Перший коректний Android-прогін: **15/15 нових регресій PASS**.
- Розширений прогін: **20/20 PASS** за 10,708 s — ті самі 15 плюс `ProxyLargeResponseTest` і всі 4 `ProxyManifestLeaseTest`, включно з отриманням 1000 сегментів через справжній HTTP proxy.

Runner: `app_process /system/bin org.junit.runner.JUnitCore`, синтетичні origin responses, справжні ProxyServer/registry/remux класи. Це виконання тестів на Android ART, не встановлення тестового APK через Instrumentation і не повний UI-прогін.

Технічні кроки підготовки: старий SDK D8 не розумів Kotlin 2.4 metadata, тому використано вже налаштований у проєкті R8 9.1.29 без зміни build configuration. Початкова спроба через голий `dalvikvm` зупинила 14 тестів на відсутніх Android JNI API до перевірок (один IP-тест пройшов). Перезапуск через `app_process` коректно ініціалізував Android runtime; після цього обидва вказані прогони завершилися успішно. Це проблеми діагностичного запуску, не додаткові знайдені баги застосунку.

SHA-256 перевіреного APK: `AA4E79119801FE2E11768A6A13E649764EC585D98E9B33C843BEB1CC31B546A2`.

Три створені тестові файли (`tests.dex`, `app-code.apk`, `junit-code.apk`) та їх порожню директорію `/data/local/tmp/uacast-proxy-regression-20260914` видалено з телефону. Вихідні APK/dex доступні в workspace; дані встановленого застосунку не чіпалися.

## Межі висновку

Виправлення стосуються двох підтверджених гонок, а не невстановленої причини Windows loopback timeout O1. Не додано socket linger, збільшення timeout, JVM workaround, retry або виключення мережевих тестів. Відтворення O1 у незалежних Java/.NET контролях описано в попередньому звіті.

Обидві підтверджені гонки D6/D7 виправлені й покриті регресіями. O1 не оголошується усунутим: позитивний Android-прогін звужує пошук, але не встановлює конкретну причину Windows TCP timeout. Роботу з Hisense VIDAA/Chromecast на реальному телевізорі, Google Play та повний UI телефонів цей етап не підтверджує. Реліз не публікувався.
