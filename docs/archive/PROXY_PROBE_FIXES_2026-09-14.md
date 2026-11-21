# Proxy: зайве читання потоку та помилки визначення формату

Дата: 2026-09-14. Продовження [перевірки upstream ownership](PROXY_UPSTREAM_HANDOFF_FIX_2026-09-14.md).

## Підтверджені дефекти

### D11 — кодек-проба виконувалася навіть з вимкненим remux

**P1, виправлено.** `ProxyRouteSelector.shouldRemuxRaw()` спочатку виконував `peekBody(128 * 1024)`, парсив TS і лише наприкінці передавав `remuxEnabled` у policy. Відповідно вимкнений remux не вимикав мережеве очікування кодек-проби.

Шлях: receiver GET/HEAD → `fetchAndServeUpstreamResource()` → `routeUpstreamResponse()` → `select()` → `shouldRemuxRaw()`. Для wrapper-плейлиста останній метод викликався з `serveUnwrappedStream()`.

Якщо origin уже віддав HTTP headers та початок потоку, але наступні байти затримувалися, proxy чекав непотрібні йому 128 КіБ або отримував timeout. Це могло завадити навіть HEAD-перевірці URL телевізором. Сам HEAD не означає HEAD до origin: proxy досі може читати wrapper/manifest для визначення фактичного маршруту.

**Зміна:** ранній вихід із `shouldRemuxRaw()` до читання, якщо remux вимкнений. Короткий sniff для відрізнення playlist від media та невелика перевірка MIME для generic binary залишилися: ці операції мають окрему функцію і не є кодек-пробою.

Детерміновані регресії дають origin лише перші 8192 байти, після яких читання кидає timeout. Direct HEAD тепер завершується 200 після одного читання; wrapper HEAD із конкретним `video/mp2t` взагалі не читає body внутрішнього потоку. Це перевірка кількості читань, не вимірювання прискорення на реальному провайдері.

### D12 — IOException під час sniff обривала відповідь без HTTP status

**P1, виправлено.** `fetchUpstreamOrRespondError()` обробляв збій отримання response, але не пізніше читання його body під час `peekBody()`. Коли короткий sniff або кодек-проба отримували reset/timeout, виняток доходив до catch у `ProxyHttpServer`, який лише логував його та закривав receiver socket. Upstream response закривався, але receiver отримував порожню відповідь замість 502.

**Зміна:** спільна вузька межа `probeUpstreamOrRespondError()` для прямого та unwrapped маршруту. Вона перетворює тільки IOException під час проби на 502; owning caller закриває response і прибирає Call із tracking у `finally`.

Body forwarding навмисно не охоплено цією межею: після вже відправленого 200 не можна дописувати другу HTTP-відповідь у відеобайти. Це перевіряє окрема регресія. Також перевірено наступний успішний запит після невдалої проби.

### D13 — внутрішня помилка wrapper origin потрапляла в кодек-пробу

**P2, виправлено.** Прямий route selector перевіряв `response.isSuccessful`, але `serveUnwrappedStream()` викликав `shouldRemuxRaw()` без такого захисту. Внутрішні 403/404 могли зависнути на читанні error body замість негайного збереження коду відповіді. За TS-подібного body також існував шлях до remux помилкової відповіді; цей варіант не відтворювався окремим тестом і не рахується окремим підтвердженим багом.

**Зміна:** `shouldRemuxRaw()` повертає false для неуспішного HTTP status до будь-якої кодек-проби. Дві регресії підтверджують передачу 403 і 404 через wrapper HEAD без читання його `video/mp2t` body.

## Змінені файли

- [ProxyRouteSelector.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyRouteSelector.kt:61>) — рання перевірка feature flag та HTTP status.
- [ProxyServer.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:381>) — межа IOException до commitment, закриття inner response на ранньому виході.
- [ProxyProbeFailureTest.kt](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyProbeFailureTest.kt>) — 9 нових регресій зі справжнім локальним HTTP proxy та синтетичним origin body.

## Початкова перевірка

До production-змін: **9 tests / 9 failures**. Після: **37/37 PASS** у точковому наборі — 9 нових і 28 попередніх routing/ownership регресій. XML збережено в `build/proxy-probe-20260914/before` та `targeted`; generated artifacts можуть бути видалені командою `clean`.

Detekt спочатку знайшов один довгий рядок у новому тесті; рядок перенесено. Жодного suppression, baseline entry, збільшення timeout або вимкнення тесту не додано.

## Фінальна перевірка

Повторно виконано всі unit test tasks (`--rerun`, JDK 21.0.11), без retries/exclusions:

| Набір | Виконань | Failures | Нові 9 регресій |
|---|---:|---:|---|
| core | 119 | 0 | — |
| Debug | 2129 | 1 | PASS |
| Release | 1914 | 1 | PASS |
| Play | 1914 | 0 | PASS |

Разом **6076 виконань, 2 failures, 0 errors**. Це variant-виконання з повтореннями, не кількість унікальних tests. Gradle загалом FAILED через Debug та Release test tasks.

Залишкові failures — відома нестабільність Windows TCP O1: `ProxyManifestLeaseTest` у Debug і `ProxyLargeResponseTest` у Release. Обидва отримали `SocketTimeoutException`. Їх не вимикали, timeout не підвищували; цей етап не виправляє O1. Play цього разу повністю PASS, що не доводить усунення нестабільності.

`lintDebug`, `lintRelease`, `lintPlay`: **0 errors / 0 warnings**. `app:detekt`, `core:detekt`, `assembleDebug`, `assembleDebugAndroidTest`, architecture boundaries, doc references, detekt baseline gate і `git diff --check`: PASS. XML повної матриці збережено під `build/proxy-probe-20260914/full-{Debug,Release,Play}`.

### Mi A2 / Android API 30

**76/76 PASS, 17,436 s**: 62 попередні proxy/ownership/large-response регресії, 9 нових та 5 route-selector tests. Зокрема на Android пройшли обидва класи, які дали Windows timeouts.

Runner: `app_process /system/bin org.junit.runner.JUnitCore`. Production-класи зі свіжого debug APK, tests із тимчасового dex, справжні HTTP sockets та синтетичний origin. Це ART regression run, не UI/Instrumentation чи реальний DLNA handshake. JVM-only `ProxyServerTest` із несумісним для D8 апострофом у назві методу, як і раніше, виконувався тільки у повній JVM-матриці; Android-набір його не включав.

APK SHA-256: `4A86532A4E0B7E578968DF3272754D4A5B4D51B81B2F0442504655FE06122DCD`.

APK не встановлювався, дані основного застосунку не змінювалися. Три створені файли runner та порожню директорію `/data/local/tmp/uacast-proxy-probe-20260914` прибрано з телефону після успішного прогону. Вихідні APK/dex залишаються у workspace. Публікації, зміни версії та віддаленого CI запуску в цьому етапі не було.

## Межі висновку

Ці дефекти доведені контрольованими помилками origin, але їхній зв'язок із конкретним Hisense VIDAA `Error de red` не доведений. Справжній телевізор і провайдер необхідні для остаточної перевірки сумісності. Цей етап не є повним UI, Google Play чи end-to-end Chromecast/DLNA тестом і не означає загальної готовності до релізу.

Наступний етап: [допуск старого запиту сегмента після restart](PROXY_RESTART_ADMISSION_FIX_2026-09-14.md).
