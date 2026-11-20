# Повторний аудит безпеки та надійності — 21.09.2026

## Висновок і межі

**Android UA Cast: перед релізом необхідно виправити A1 — відтворене виснаження heap через XMLTV.**
Нових підтверджених P0 у перевірених шляхах не виявлено. Це не гарантія відсутності інших дефектів.

Аудит виконано для поточного робочого дерева, включно з незакоміченими попередніми виправленнями. Нових змін у виробничий код або тести не внесено; додано лише цей звіт. Старі зміни не скасовано. Публікацію, встановлення APK і вимкнення комп'ютера не виконували.

Основний обсяг: XMLTV/EPG, імпорт і кешування плейлистів, DLNA discovery/control, proxy admission/serving/remux, lifecycle Media3/Cast, APK updater/signature gate, manifest/FileProvider/WebView, обробка скасування, CI. Простежено критичні міжфайлові шляхи; це не твердження про построкове покриття кожного файла.

У корені також є окремий, наразі untracked вебщоденник із Firebase та Node-сервером. Його проблеми наведено окремо: **вони не є вразливостями Android APK**. Наявність локального коду не доводить, що цей сайт або сервер уже опубліковані.

Позначення: P1 — виправити перед релізом відповідного компонента; P2 — суттєвий дефект з обмеженим/умовним впливом. «Відтворено» нижче означає конкретний локальний експеримент, не перевірку на телевізорі чи телефоні.

## Android: підтверджені дефекти та джерела ризику

### A1 — P1: невеликий gzip XMLTV може вичерпати пам'ять застосунку

- **Файли/функції:** `app/src/main/kotlin/com/uacastplayer/epg/XmlTvParser.kt:181`, `internChannelId()`; `:194`, `:199`, `:201`, `startElement()`; `epg/EpgDocumentPipeline.kt:31`, `parse()`; `data/epg/EpgDownloader.kt:111`; `data/epg/EpgRepository.kt:63`.
- **Шлях:** EPG URL → обмежене завантаження → gzip-декомпресія → SAX → збереження повних `id`/`channel`/`icon src` → побудова EPG.
- **Причина:** ліміт 512 символів поширюється на текст назв, але не на XML-атрибути. Пул обмежує кількість ID, не їхню довжину чи сукупний обсяг. Ліміт завантаження 96 MiB не обмежує розпакований `.gz`. `HeapBudget.maxProgrammes()` не захищає пам'ять, зайняту атрибутами каналів.
- **Відтворення:** справжній скомпільований `EpgDocumentPipeline.parse()` у JVM із heap 134217728 байтів; нормальний XML проходить. Згенеровано gzip на **350123 байти**, 16000 каналів, унікальний ID із префіксом і 16384 повторюваними символами. Кількість каналів нижча за `MAX_CHANNELS=25000`.
- **Фактичний результат:** `PRODUCTION_EPG_PIPELINE_OOM=Java heap space`. Генерація вхідного gzip завершилась до виклику парсера; OOM виник саме під час production parse.
- **Очікується:** контрольована відмова/обрізання неприйнятних метаданих, збереження попереднього EPG, без падіння процесу.
- **Наслідок:** crash/DoS через недовірений EPG. `EpgRepository` перехоплює `Exception`, а не `OutOfMemoryError`; саме лише додавання catch для OOM не усуває проблему. Адреса EPG може також надходити з метаданих плейлиста через `EpgSourceAutoDetect`.
- **Виправлення:** обмежити довжини ID/URL до інтернування та збереження; додати сукупний бюджет утримуваних метаданих. Окремо обмежити розпакований потік і великі XML-токени до небезпечної алокації всередині SAX. Загальний ліміт розпакованих байтів сам по собі не замінює бюджет атрибутів.
- **Обмеження перевірки:** JVM/SAX; Android Expat/ART у цьому проході не запускали. Відсутність обмежень у production-коді підтверджено незалежно від реалізації SAX.

### A2 — P2: один SSDP endpoint із портом 0 зриває весь пошук DLNA

- **Файли/функції:** `dlna/UpnpHttpEndpoint.kt:23`, `isSupported()`; `dlna/SsdpDiscovery.kt:90`, `discover()`; `:209`, `fetchDevice()`; `dlna/DlnaSessionRepository.kt:240`, `discoverDevices()` — усі під `app/src/main/kotlin/com/uacastplayer/`.
- **Тригер:** SSDP-відповідь із `LOCATION: http://192.168.1.10:0/desc.xml`.
- **Доказ:** фактичний `UpnpHttpEndpoint.absolute()` повернув URL без відмови; фактичний `Request.Builder().url()` кинув `IllegalArgumentException: Invalid URL port: "0"`.
- **Шлях відмови:** `uri.port in -1..65535` допускає 0 → Request будується **до** `try` → помилка async-дитини скасовує `coroutineScope/awaitAll` → repository перетворює всю операцію на `emptyList()`.
- **Очікується:** пропустити один некоректний пристрій, зберегти справні результати.
- **Фактично:** можна втратити весь список. **Не називати це crash застосунку:** зовнішній catch його перехоплює.
- **Виправлення:** дозволяти відсутній порт або 1..65535; включити побудову Request у per-device error boundary; тестувати змішаний пакет із справним і несправним пристроями.

### A3 — P2: повільний опис пристрою затримує всі результати DLNA

- **Файли/функції:** `dlna/DlnaSessionRepository.kt:106`, створення `discoveryHttpClient`; `dlna/SsdpDiscovery.kt:90`, `:212`, `:226`.
- **Тригер:** renderer/SSDP endpoint надсилає XML маленькими порціями, кожна швидше за 4-секундний read timeout, але весь документ передається дуже довго.
- **Доказ:** встановлено лише `connectTimeout(4s)` та `readTimeout(4s)`, без `callTimeout`. У локальному source JAR реально використаного OkHttp 5.4.0 `Builder.callTimeout = 0`. `BoundedByteReader` обмежує байти, не час; `awaitAll()` очікує останній endpoint.
- **Наслідок:** список не показується, хоча інші телевізори вже відповіли; довго живуть запит і multicast lock. Це не Main-thread ANR. Закриття sheet має шлях скасування через `executeCancellable`, тому «невбиваний запит після закриття» не стверджується.
- **Виправлення:** загальний deadline для finite device-description call; ізольований timeout кожного endpoint; за потреби публікувати справні результати поступово.
- **Статус:** доведено конфігурацією та шляхом коду. Slow-drip тест на Android ще потрібен. Спроба створити Android-варіант OkHttpClient у звичайному JShell зупинилась через відсутній `android.util.Log`; це обмеження стенду, не баг застосунку.

### A4 — P2, безпека: недовірена SSDP LOCATION задає довільний HTTP GET

- **Файли/функції:** `dlna/SsdpDiscovery.kt:199`, `collectLocationFrom()`; `:209`, `fetchDevice()`; `dlna/UpnpHttpEndpoint.kt:23`; `dlna/DlnaSessionRepository.kt:106`.
- **Тригер:** вузол тієї самої LAN відповідає на активний M-SEARCH і вказує LOCATION, що не належить цьому вузлу.
- **Доказ:** адреса `packet.address` не бере участі у перевірці LOCATION. Фактичний валідатор і Request builder приймають `127.0.0.1`, `169.254.169.254` та сторонню IPv4-адресу. Реальних запитів до цих адрес під час аудиту не надсилали. Discovery client також не вимикає стандартні redirects.
- **Шлях:** UDP → LOCATION → автоматичний GET опису пристрою **до вибору renderer користувачем**. Перевірка same-host для control URL в XML захищає інший, пізніший етап.
- **Наслідок:** обмежений blind SSRF — примусові GET-запити з телефона до небажаних endpoint. Доведень читання секретів, повернення відповіді атакувальнику чи RCE немає. Потрібні присутність у LAN і активний discovery; це не доведена інтернет-доступна атака.
- **Виправлення:** перевіряти відповідність LOCATION відправнику SSDP/дозволеним адресам renderer; відхиляти loopback, unspecified, multicast та broadcast як HTTP target. Контролювати resolved address і кожен redirect, враховуючи legitimate multi-interface devices. Не блокувати всі приватні адреси: DLNA потребує LAN.

### A5 — P2: переведення годинника назад може залишити старий remux-буфер

- **Файли/функції:** `data/cast/ProxyResourceRegistry.kt:195`, `beginDraining()`; `proxy/RemuxHandoffPolicy.kt:15`; `proxy/RemuxSegmentBuffer.kt:12`.
- **Тригер:** після заміни raw-TS каналу, протягом grace period системний час зміщується назад; новий канал не підтверджує завантаження.
- **Доказ:** є один `Thread.sleep(10000)`, потім `System.currentTimeMillis() - startedAt`. Якщо час відкориговано на -60 секунд, після реальних 10 секунд elapsed буде близько -50 секунд. `shouldKillDraining()` поверне false. Повторного таймера немає.
- **Наслідок:** `drainingRemuxSession` з буфером до 48 MiB утримується до наступної заміни, підтвердження або stop. Старий upstream вже зупиняється — це **не** доказ подвійного мережевого стриму чи нескінченного росту пам'яті.
- **Виправлення:** monotonic clock для elapsed/deadline; гарантований cleanup після grace period. Переглянути споріднені wall-clock deadlines у `RawTsRemuxSession.kt:119` та `SsdpDiscovery.kt:112`.
- **Статус:** підтверджений умовний шлях у коді; годинник комп'ютера/телефона під час аудиту не змінювали.

## Додатковий Android-ризик, який потребує окремого відтворення

### R1 — P2-кандидат: скасування SAF-імпорту не закриває блокувальний read

`data/playlist/PlaylistFileLoader.kt:94` використовує `withContext(IO)`, `openInputStream()` і синхронний `BoundedByteReader.readBytes()`. Для цього stream немає cancellation handler, тоді як `documentName()` окремо застосовує `CancellationSignal`. `PlaylistController.cancelPendingSourceAdd()` скасовує Job, але це не перериває довільний блокувальний `InputStream.read()`.

Якщо зовнішній DocumentsProvider поверне pipe і перестане віддавати байти, IO-thread і stream можуть пережити закриття екрана до завершення/помилки провайдера. Не стверджується для звичайного локального файла. Потрібен fake DocumentsProvider із завислим pipe; перевірити закриття FD та завершення Job після cancel. Потенційне виправлення — cancellation-aware open/read із явним закриттям stream/descriptor.

## Окремий вебщоденник: відтворені проблеми

### W1 — P1: порушення ізоляції акаунтів на клієнті

- **Файл/функції:** `firebase-auth.js:108`, `cloudRef()`; `:115`, `queueCloudSave()`; `:119`, `loadCloud()`; `:143`, auth callback.
- **Шлях 1:** починається get для A → currentUser стає B → відповідь A завершується → без UID/generation guard `setData()` застосовує дані A → наступний `saveCloud()` пише їх у B.
- **Шлях 2:** після виходу залишаються локальні дані A → вхід у B без cloud document → `!snapshot.exists` автоматично запускає `saveCloud()` без підтвердження імпорту.
- **Відтворення:** незмінні тіла реальних функцій виконано в Node VM з fake Firestore та синтетичними даними. Отримано `currentUid: B, local.owner: A`, потім запис `{uid: B, data.owner: A}`. Окремо підтверджено auto-upload даних A для нового B.
- **Наслідок:** чужі приватні записи в локальному UI/хмарному сховищі іншого акаунта; можливе перезаписування даних B.
- **Виправлення:** локальне сховище, pending saves і запити прив'язати до UID/session generation; при зміні акаунта скасувати таймери та відкидати stale completion. Не переносити guest/попередні дані автоматично — потрібна явна дія імпорту.
- **Важливо:** Firestore rules перевіряють UID правильно. Помилка в тому, які саме дані клієнт записує під дозволеним поточним UID; це не «публічно відкрита Firebase база».

### W2 — P1: Firebase-синхронізація втрачає паралельні зміни

- **Файл/функція:** `firebase-auth.js:113`, `saveCloud()`.
- **Тригер:** два пристрої/вкладки одного користувача завантажують спільний стан, кожен додає інший запис, обидва зберігають.
- **Доказ:** безумовний `.set()` замінює весь diary, без перевірки базової revision/transaction. У локальному harness обидва реальні `saveCloud()` завершились успішно; збереглись `base + note B`, `note A` втрачено. Fake storage моделював саме replacement, заданий клієнтським викликом.
- **Очікується:** обидві зміни збережені або другий клієнт отримує явний conflict.
- **Виправлення:** optimistic revision у transaction та conflict UI, або гранулярне зберігання записів зі стабільними ID і правилами merge/delete. Timestamp сам по собі не запобігає перезаписуванню.

### W3 — P2: REST-перевірка конфлікту пропускає stale baseVersion і некоректний If-Match

- **Файл/функція:** `diary-server.mjs:220`, PUT `/api/diary` у `withWriteLock()`.
- **Доказ:** `body.baseVersion` перевіряється на наявність/числовість, але не на рівність поточній версії. Некоректний `If-Match` стає NaN і пропускає перевірку через `Number.isFinite()`.
- **Відтворення реального validation block:** current=7, `If-Match: "6"` → conflict; current=7, лише `baseVersion:6` → accepted; `If-Match: garbage` → accepted.
- **Наслідок:** stale API client може мовчки перезаписати свіжі дані. Коректний клієнт із числовим If-Match цей конкретний дефект не активує. Bearer-authentication не обходиться.
- **Виправлення:** однозначно розбирати precondition; невірний формат відхиляти; і для header, і для body порівнювати очікувану версію з current. Сформулювати окрему політику для `*`.

### W4 — P2 локально / P1 при зовнішньому доступі: malformed Host завершує Node-процес

- **Файл/функція:** `diary-server.mjs:186`, async request handler; `:195`, `new URL()`.
- **Тригер:** GET `/` з Host `[`. URL parsing розташований до автентифікації та до route try/catch.
- **Відтворення:** фактичне тіло handler виконано в окремому Node v24.19.0 через локальний синтетичний `server.emit('request', ...)`, без відкриття listener. Результат: `ERR_INVALID_URL`, **exit code 1** через необроблений rejection.
- **Наслідок:** один некоректний запит може зупинити сервер, якщо він досягає handler. За замовчуванням HOST=127.0.0.1; прямого інтернет-доступу не доведено. Reverse proxy може сам відхилити Host — залежить від конфігурації.
- **Виправлення:** не довіряти Host для route parsing; використовувати фіксований base URL, перевіряти request-target і обгортати весь async handler у контрольовану обробку помилок із HTTP 400 для некоректного запиту.

## Перевірені захисти: не записувати у баги без нових доказів

- `ProxyRequestTarget` перевіряє session token через `MessageDigest.isEqual`; proxy має bounded queues, per-IP leases, admission deadline і закриття client sockets. Саме слухання `0.0.0.0` потрібне LAN receiver і не доводить відсутність авторизації.
- `ApkSignatureGate` перевіряє package та signing certificate/допустиму історію ротації. Відсутність release checksum сама по собі не означає можливість встановлення APK з чужим підписом.
- Play manifest видаляє REQUEST_INSTALL_PACKAGES / UPDATE_PACKAGES_WITHOUT_USER_ACTION та install receiver; sideload updater не слід автоматично трактувати як проблему Play-варіанта.
- Diagnostics FileProvider не exported і відкриває лише `cache/diagnostics/`.
- Privacy-policy WebView має вимкнені JavaScript, file/content access, network loads і mixed content; викликає destroy при disposal.
- XMLTV вимикає external entities і має resolver, що повертає порожній документ. A1 — не доказ XXE; це виснаження пам'яті метаданими.
- У перевірених production HTTP clients не знайдено trust-all TLS/hostname verifier. Cleartext для IPTV/DLNA — реальна транспортна межа, але не новий універсальний «критичний баг» без конкретного шляху атаки.
- Firestore rules обмежують document за authenticated UID. Firebase web API key у конфігурації сам по собі не є серверним секретом.
- У перевірці tracked filenames не знайдено JKS/P12/PEM/private signing properties. Це вузька перевірка, не аудит всієї історії Git на витоки.

## Що насправді перевірено тестами

Нові локальні експерименти цього проходу:

1. Production EPG pipeline: нормальний XML проходить; adversarial gzip → OOM при 128 MiB.
2. Production UPnP validator + OkHttp Request: порт 0 приймається першим і відхиляється другим; loopback/link-local/сторонній host приймаються обома.
3. Реальні Firebase helper functions у fake async environment: stale account response, автоматичний cross-account upload, concurrent last-write loss.
4. Реальний REST precondition block: коректний stale header відхиляється, stale body/NaN header проходять.
5. Реальне тіло Node handler в окремому процесі: malformed Host → exit 1.

Живий Firebase, сторонні endpoint, реальний телевізор і телефони не використовувались. Нічого з синтетичних даних у зовнішні сервіси не записували.

Наявні XML-звіти Gradle датовані **14.09.2026**, не сьогодні: core 119/0 failures; debug 2138/0; release 1919/1; play 1919/0. Це залишки різних запусків, **не новий узгоджений зелений gate**. Release failure — `ProxyLargeResponseTest`, `received=262082/262144`. Новий `ProxyWriteTimeoutTest` має debug-звіт 4/4, але release-звіту для нього немає. Цю прогалину не можна позначати завершеною перевіркою релізу.

## Порядок виправлення та обов'язкова регресія

1. **A1:** бюджети XML атрибутів/розпакування; тести gzip з великими ID/src, одним гігантським атрибутом, 128 MiB heap, cancellation, нормальними великими XMLTV. Підтвердити на ART.
2. **A2–A4:** спільне посилення SSDP trust boundary: порт/host/redirect, per-device failure isolation, загальний deadline; mixed good/bad devices та slow-drip fixtures.
3. **A5:** monotonic timers; керований clock regression test, cleanup без confirmation.
4. **R1:** fake blocked DocumentsProvider, cancellation/FD regression, лише потім робити висновок про вплив на конкретні телефони.
5. **Окремо веб:** W1 і W2 перед увімкненням реальної multi-user/multi-device синхронізації; W4 перед зовнішнім доступом; W3 для API concurrency contract.
6. Після виправлень — повний свіжий debug/release/play gate, окрема перевірка незакритого large-response failure, Android lifecycle/network tests і Hisense VIDAA compatibility test.

Не завершено цим проходом: зовнішній SCA/CVE-аудит усіх залежностей, незалежний security review backend/Play Billing, живі Firebase rules/emulator tests, підтвердження на Hisense, повне виконання UI/інструментальних тестів на всіх Android API. Відсутність нового finding у цих частинах не є сертифікатом безпеки.
