# Proxy: допуск старого запиту сегмента після restart

Дата: 2026-09-14. Продовження [перевірки помилок визначення формату](PROXY_PROBE_FIXES_2026-09-14.md).

## D14 — запит попереднього HTTP-з'єднання міг відкрити upstream після restart

**P2, підтверджено та виправлено.** Це порушення ізоляції життєвого циклу, не підтверджена причина конкретної помилки Hisense.

До виправлення `trackUpstreamCall()` перевіряв `acceptingUpstreamCalls`, `httpServer.isRunning` та channel owner. Для finite media остання перевірка навмисно пропускалася, щоб уже обіцяні сегменти могли завершитися при перемиканні каналу.

Однак глобальні прапорці не відрізняють початковий сервер від повторно запущеного:

1. Receiver отримує manifest і запитує segment URL.
2. Старий response worker знаходить `ResourceEntry` і починає створювати `Call`, але ще не додає його до `upstreamCalls`.
3. `stop()` знімає snapshot зареєстрованих Calls, закриває старі sockets і перериває workers. Призупинений Call до snapshot не потрапляє.
4. `start()` знову встановлює running/accepting у true.
5. Старий worker продовжується. Оскільки finite media не потребує current channel owner, Call допускається і виконує запит до старого origin, хоча його receiver socket уже закритий.

Повторне використання token не є необхідною умовою: відтворено і з новим token. Старий worker уже пройшов розбір URL та отримав resource до restart.

Наслідок — непотрібний запит до провайдера поза початковим життєвим циклом. Він може займати мережеві ресурси та конкурувати з новим відтворенням. Конфлікт із реальним provider connection limit не відтворювався; твердження про гарантований збій playback тут не робиться.

## Виправлення

[ProxyHttpServer](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyHttpServer.kt>) прив'язує до `ParsedRequest` перевірку відкритості саме початкового локального socket. Це не перевірка доступності віддаленого receiver: `Socket.isClosed` означає локальне закриття, зокрема під час stop.

[ProxyServer](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt>) передає цю перевірку через прямий та unwrapped шлях і виконує її під `producerLock` разом із допуском Call. `stop()` спочатку забороняє admission під тим самим lock, а `start()` знову дозволяє його тільки після завершення teardown старих sockets. Старий socket не стає відкритим після restart — навіть якщо token, channel URL або port повторюються.

Відхилений Call скасовується до `execute()`. Запис 503 у вже закритий receiver socket може завершитися звичайним SocketException; цей socket не відновлюється і нове origin-з'єднання не відкривається. Уже чинні правила channel owner та finite-segment handoff залишено.

## Доказ і регресії

[ProxyRestartAdmissionTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyRestartAdmissionTest.kt>) використовує справжній локальний HTTP proxy та синтетичний OkHttp origin. `EventListener.Factory` призупиняє перший segment Call до admission. Затримка моделює допустиме перемикання потоків; нового test hook у production не додавалося.

Початковий прогін: **5 tests, 3 failures**. В усіх трьох assertion фіксував **1 origin fetch замість 0**, а не timeout чи нестабільність Windows TCP.

Після виправлення перевірено:

- same-token restart відхиляє старий finite request;
- different-token restart також його відхиляє;
- stop без restart залишається безпечним;
- channel handoff без restart усе ще дозволяє завершити finite segment;
- новий сегмент після restart успішно завантажується.

Точковий набір нових та попередніх ownership/probe regressions: **37/37 PASS**. XML збережено в `build/proxy-restart-20260914/before` та `targeted`; це generated artifacts, які може видалити `clean`.

Detekt початково вказав на складність нової умови та довгий рядок; умову поділено за змістом, рядок перенесено. Правила, baseline та test timeouts не послаблено.

## Повна локальна матриця

JDK 21.0.11; усі test tasks виконано повторно через `--rerun`, без retries чи exclusions:

| Набір | Виконань | Failures | Нові 5 регресій |
|---|---:|---:|---|
| core | 119 | 0 | — |
| Debug | 2134 | 2 | PASS |
| Release | 1919 | 1 | PASS |
| Play | 1919 | 0 | PASS |

Разом **6091 виконання, 3 failures, 0 errors**. Variant-прогони повторюють частину тих самих tests; це не кількість унікальних tests. Gradle загалом FAILED через Debug і Release test tasks.

Failures — відома Windows TCP нестабільність O1: `ProxyLargeResponseTest` у Debug/Release, `ProxyManifestLeaseTest` у Debug. В усіх випадках `SocketTimeoutException`. Play цього разу PASS, але це не усуває нестабільність. Обидва проблемні класи окремо пройшли на Mi A2 у наборі нижче.

`lintDebug`, `lintRelease`, `lintPlay`: **0 errors / 0 warnings**. `app:detekt`, `core:detekt`, `assembleDebug`, `assembleDebugAndroidTest`, architecture boundaries, doc references, detekt baseline gate та `git diff --check`: PASS. XML збережено під `build/proxy-restart-20260914/full-{Debug,Release,Play}`.

## Mi A2 / Android API 30

**81/81 PASS, 17,93 s** через Android ART: 76 попередніх регресій плюс 5 нових restart-admission tests. Зокрема пройшли великі HTTP-відповіді та 1000 segment URLs.

Запуск: `app_process /system/bin org.junit.runner.JUnitCore`, production-класи зі свіжого debug APK, tests із тимчасового dex, синтетичний origin. Це не UI/Instrumentation run і не handshake з телевізором. Як і раніше, JVM-only `ProxyServerTest` з апострофом у назві методу не входив до Android dex-набору; він залишається в усіх звичайних JVM variant tasks.

SHA-256 APK: `ECD3592977AB7FAD684D832E6C758ED71BFBF15D119291C82E6152072ADFF7F4`.

APK не встановлювався; дані основного застосунку не змінювалися. Після успішного прогону видалено тільки три створені файли та порожню директорію `/data/local/tmp/uacast-proxy-restart-20260914`; оригінали APK/dex залишилися у workspace. Публікації, зміни версії або запуску віддаленого CI в цьому етапі не було.

## Межі висновку

Це вузьке виправлення request admission після restart. Воно не додає socket write timeout, не доводить відсутність усіх сценаріїв backpressure та не виправляє відому нестабільність великих TCP-відповідей на Windows. Реальна трансляція на Hisense VIDAA потребує окремої перевірки.
