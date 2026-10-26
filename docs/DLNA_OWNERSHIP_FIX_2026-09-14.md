# DLNA HLS ownership: виправлення та перевірка — 14.09.2026

Наступний етап: [D9/D10 — direct/wrapper TS, manifest ownership і раннє звільнення remux](<C:/UA CAST/docs/PROXY_UPSTREAM_HANDOFF_FIX_2026-09-14.md>), включно з 62 Android-регресіями. Нижче збережено результати D8 на момент їх перевірки.

Продовження [D6/D7 proxy fixes](<C:/UA CAST/docs/PROXY_RACE_FIXES_2026-09-14.md>). Знайдено й виправлено ще одну підтверджену проблему — D8. Зміни локальні: версію, CI, налаштування Windows та встановлену програму на телефоні цього етапу не змінювали; реліз не публікували.

## D8 — P1: flattened HLS залишався власністю старого каналу

### Доказ і шлях виконання

[DlnaSessionRepository.prepareProxy](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/dlna/DlnaSessionRepository.kt:475>) повторно використовує proxy/token при зміні каналу й викликає `registerPlaylist`. Потім `connectBlocking` посилає SOAP Stop; його відмову свідомо не вважає причиною скасувати нове підключення.

До цього виправлення:

- `ProxyServer.registerPlaylist` змінював лише registry. Уже активний `HlsFlattenedStream` не зупинявся і його HTTP Call не скасовувався.
- Перевірка replay використовувала тільки running/token. Обидва залишаються незмінними при звичайному перемиканні каналу.
- `serveFlattenedHls` читав token вже **після** очікування origin/callback. Тому запізнілий старий handler міг зареєструвати replay у новій сесії, зокрема після restart із тим самим token.
- Відсутність нового channel lease також не забороняла старому URL створити свіжий flattened producer. Обмеження graph для rewritten HLS не захищало цей маршрут: flatten виконується перед rewrite.

У тестах зі справжнім ProxyServer, HTTP sockets, HLS replay та контрольованим синтетичним origin отримано **5 failures із 6 tests**. Зокрема, після вибору B Call старого сегмента A залишався нескасованим; запізнілий A повертав HTTP 200 із DLNA MIME або робив другий manifest fetch після restart. Контроль повторної реєстрації незміненого A проходив.

Наслідок: зайві паралельні origin requests, витрата мережі/батареї; за обмежень провайдера на кількість з'єднань це може заважати новому каналу. Нескасований Call і повторний replay підтверджені; фактичну відмову конкретного провайдера чи телевізора цей тест не доводить. Це одна проблема володіння з кількома сценаріями, не п'ять незалежних багів.

### Виправлення

У [ProxyServer](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:195>):

- Flattened stream зберігається разом із незмінним channel lease, захопленим перед початковим fetch, як і на remux-маршруті.
- Зміна root і допуск нового replay серіалізовані через один `flattenedStreamsLock`. Старі producers зупиняються, їх поточні Calls скасовуються незалежно від SOAP Stop телевізора.
- [Допуск replay](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:574>) порівнює ідентичність початкового lease з актуальним. A → B → A і same-token restart не відновлюють старі права.
- Відхилений запит отримує 503, якщо його socket ще живий; він не переходить до fallback старого manifest.
- Повторна реєстрація того самого каналу не перериває його replay. Звичайний unsupported-HLS fallback і remux draining залишаються без змін.

Використано вже наявний `RemuxRequestLease` як спільну ідентичність власника двох producer-маршрутів; нового незалежного лічильника поколінь не додано. Порядок locks: flattened admission → registry remux → manifests. Registry не захоплює flattened lock у зворотному напрямку.

## Регресії та результати

[ProxyFlattenedOwnershipTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyFlattenedOwnershipTest.kt>) — 6 нових tests:

1. Зміна каналу скасовує старий заблокований segment Call без очікування renderer Stop.
2. Незмінний канал зберігає активний replay і завершує відповідь успішно.
3. Запізнілий manifest старого каналу відхиляється.
4. A → B → A не відновлює старий lease.
5. Restart із тим самим token не допускає старий request у нову сесію.
6. Старий URL після перемикання не створює новий producer.

Для restart-тесту перевіряється завершення handler саме старого response pool: EOF закритого socket сам по собі ще не означає завершення його роботи.

Точковий прогін: **42/42 PASS** — 6 нових, 14 remux ownership/race, 14 renderer-profile та 8 flattened-stream tests. Початковий detekt вказав на три довгі рядки нового тесту; їх перенесено без зміни правил, baseline чи assertions.

Повна JVM-матриця на JDK 21.0.11, кожен test task виконано примусово заново:

| Набір | Виконань | Failures |
|---|---:|---:|
| core | 119 | 0 |
| Debug | 2106 | 1 |
| Release | 1891 | 1 |
| Play | 1891 | 1 |

Разом **6007 виконань, 3 failures, 0 errors**. Це сума variant-прогонів, не кількість унікальних tests. Усі 6 нових регресій проходять у кожному app variant. Failures — відомі Windows TCP timeouts: `ProxyManifestLeaseTest` у Debug/Release і `ProxyLargeResponseTest` у Play. Кількість timeouts змінилася проти попереднього прогону; це не доказ покращення TCP, оскільки збій нестабільний.

`lintDebug`, `lintRelease`, `lintPlay`: 0 errors, 0 warnings. `app:detekt`, `core:detekt`, `assembleDebug`, `assembleDebugAndroidTest`: PASS. Загальна Gradle-команда завершилась FAILED через зазначені три test tasks, не через компіляцію чи static analysis. Тести не виключалися, їх timeouts/retries не змінювалися.

XML до виправлення, точкового прогону та повної матриці збережено під `build/dlna-ownership-20260914/{before,targeted,full-Debug,full-Release,full-Play}`. Це generated artifacts, які може видалити `clean`.

### Mi A2 — Android ART

Mi A2 / API 30: **48/48 PASS**, 15,977 s. Набір: нові 6, попередні 15 remux/IP регресій, `ProxyLargeResponseTest`, усі 4 `ProxyManifestLeaseTest`, 14 renderer-profile та 8 flattened-stream tests. Зокрема, пройшов справжній HTTP-сценарій із 1000 segment URLs та велика відповідь повільним concurrent receivers.

Runner: `app_process /system/bin org.junit.runner.JUnitCore`; synthetic origin, production proxy classes зі свіжого debug APK. Це виконання на Android ART, **не** UI/Instrumentation-прогін і не тест справжнього телевізора. Dex зібрано на вже налаштованому R8 9.1.29; початкову помилку команди розпакування test dependency виправлено до запуску на пристрої.

SHA-256 APK: `C02AF13C87255056F6B7EB9F8B52316DDCA108180E857689E2308289605502DD`.

Три тимчасові файли (`tests.dex`, `app-code.apk`, `junit-code.apk`) і порожню директорію `/data/local/tmp/uacast-dlna-ownership-20260914` видалено. Вихідні APK/dex залишилися в workspace. Встановлену програму та її дані не змінювали.

## O1 — уточнення Windows TCP timeout, не виправлення

У [незалежний Java control](<C:/UA CAST/docs/debug/2026-09-14/SocketTransferProbe.java>) додано два діагностичні параметри, без зміни production server:

- `-Dprobe.holdOpen=true`: writer не закриває socket до читання всього тіла peer; очікування обмежене. Перевіряються всі bytes і EOF, не лише факт запису сервером.
- `-Dprobe.receiveBuffer=65536`: збільшений client receive buffer.

| Контроль, payload 256 KiB, 3 concurrent clients | Transfers | Failures |
|---|---:|---:|
| Close одразу, buffer 8 KiB | 18 | 3 |
| Close одразу, buffer 64 KiB | 18 | 2 |
| Hold-open до читання body, buffer 8 KiB | 9 + 18 + 60 | 0 |

У failing controls: writer завершився, client чекає хвіст, server `FIN_WAIT_1`, client `ESTABLISHED`. Окремий останній hold-open прогін дав 60/60 PASS. Спостереження пов'язує симптом із завершенням TCP connection на цьому хості, але не доводить конкретний дефект Windows, JVM чи filter driver. Збільшення буфера не усуває його стабільно.

Hold-open control використовує тестовий сигнал від читача; у звичайного HTTP receiver такого application-level acknowledgment немає. Тому цей дослід не перенесено як довільний sleep/linger у proxy. Packet trace з необхідними правами та аналіз Windows networking залишаються наступним кроком для O1; драйвери/firewall/TCP settings не змінювалися.

## Вердикт етапу

D8 виправлено та перевірено на JVM і Mi A2. O1 залишається відкритим; повний Windows gate не зелений. Немає підтвердження, що саме D8 або O1 спричинили Hisense VIDAA `Error de red`: потрібен повтор на телевізорі. Не перевірялися Google Play, UI телефону чи повний end-to-end Chromecast/DLNA handshake. Цей етап не є доказом готовності всього застосунку до релізу.
