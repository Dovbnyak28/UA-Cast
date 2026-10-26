# DLNA XML та MIME локального файла: стабілізаційний прохід

## Перевірено й виправлено

### 1. P2 — SOAP-помилка не розпізнається через XML-представлення

Старий `UpnpFault.parse` шукав буквальний `<errorCode>`. Prefix, атрибути
та numeric XML entities перетворювали коректний 701 на невідомий код.
Траса: HTTP 500 → UpnpFault → AvTransportClient.Play → FAILED замість
TRANSITIONING → відсутність повтору → teardown proxy. Qualified 401 також
не міг коректно позначити unsupported status action старого renderer.

Негативний parser-тест упав. Окремий реальний client/interceptor сценарій
500/qualified-701 → 200 теж упав: Play повертав false після першої відповіді.
Після правки він перевіряє success і саме два запити, без зміни retry budget.

### 2. P2 — volume/status XML втрачає коректне числове значення або стан

`VolumeResponseParser` вимагав точний `<CurrentVolume>`, а transport-state
regex не читав CDATA. Атрибути / prefixes / entities могли робити volume
unknown і вимикати slider. Коректний STOPPED у CDATA ставав UNSUPPORTED,
замість INACTIVE для watchdog. Це підтверджені parser/policy сценарії,
не отримані логи конкретного Hisense.

Замість трьох різних literal/regex-пошуків використано один bounded reader
плоских SOAP-полів. Він підтримує XML text, namespaces через local qName,
атрибути, CDATA, predefined/numeric entities, envelope-free siblings та
історичні unbound-prefix фрагменти. Не довіряє вкладеній розмітці як числу.

UPnP guide вимагає враховувати XML namespace mechanism та ігнорувати
непотрібні атрибути, а не залежати від конкретного spelling тегів.
[OCF implementation guide](https://openconnectivity.org/wp-content/uploads/2015/11/UPnP_Vendor_Implementation_Guide_Jan2001.htm).

### 3. P2 — назва файла перекриває конкретний MIME провайдера

`LocalVideoFormats` раніше віддавав розширенню пріоритет над конкретним
`ContentResolver.getType`. Для Alias.mp4 + video/x-matroska назовні йшов
video/mp4: через LocalVideo → proxy Content-Type / Cast / DLNA metadata.
Це неправильний опис байтів і можливе відхилення renderer.

Виправлення: конкретний, валідований video MIME (або відомий legacy MIME)
провайдера має пріоритет. Generic video/*, application/octet-stream,
відсутній або небезпечний MIME зберігають безпечний extension fallback.
Відеоформат не визначається magic-byte sniffing і provider може помилятися;
це дотримання metadata API, не гарантія підтримки кодека.
[ContentResolver.getType](https://developer.android.com/reference/android/content/ContentResolver#getType(android.net.Uri)).

### 4. P2 — локальний HLS-аліас приймається як finite video file

`video/x-mpegurl` проходив generic video/* перевірку, хоча локальний proxy
file serving не переписує relative segment URIs усередині HLS. Playlist
потрапляв у неправильний механізм. Явні відомі HLS MIME aliases тепер
відхиляються в file picker, навіть за misleading.mp4. Звичайний HTTP HLS
через існуючий playlist pipeline не змінений.

## Мінімальний shared XML механізм і безпека

Той самий SAX hardening винесено з DeviceDescriptionParser у UpnpXmlReader;
description-policy / URL resolution / service selection не змінені.
Фабрика залишається в qName mode для сумісності з попередніми legacy
fragment тестами. SOAP reader має незалежні межі: 64 KiB input, 64 nesting
levels, 512 символів на field; DTD відхиляється до parser, resolver порожній,
external entities вимкнені де підтримуються. Невідомі platform features не
приховують fatal JVM errors. Некоректний / truncated XML не публікує partial
values і не друкує body у лог. Немає нових dependency, jobs, scopes або
зміни retry/admission/budget правил.

Відкинуто підозру щодо stop → reconnect ordering: поточний stop уже
резервує FIFO mutex lane через CoroutineStart.UNDISPATCHED до повернення.
Цей захист не перероблявся. DLNA cancellation/volume/network generation
контролі перевіряються у focused/full regressions.

## Негативні та позитивні результати

- Перед production-правками: **22 tests / 4 failures** — саме нові XML,
  volume, MIME-precedence, HLS-alias кейси; 18 попередніх controls пройшли.
- Client retry: **3 tests / 1 failure** нового qualified-701 кейса.
- Transport/watchdog parser: **7 tests / 1 failure** нового CDATA кейса.
- Перша implementation: **137/137 host tests**, але gate відхилений через
  дві MaxLineLength помилки. Rules/baseline не послаблені. Після переносу
  рядків повторний gate пройшов за **2 хв 27 с**: 137 tests, нуль
  failures/errors/skips, lintDebug, Detekt та debug/test APK успішні.

Усі **70** scoped Android source-файлів primary/review checkout збігаються.
Дванадцять repository checks пройшли. **Повний gate пройшов за 8 хв 50 с**
(227 завдань: 27 виконано, 200 up-to-date): **Debug 2722, Release 2328,
Play 2328**, нуль failures/errors/skips; незмінні Core 133 результати
повторно використано. **49 screenshot goldens незмінні**, нуль added/changed;
три recorded diagnostic previews у ignored build не є новими еталонами.

Lint Debug/Release/Play має нуль errors/warnings, по два наявні Hints щодо
target API 36 / Compose BOM. Kotlin compiler warnings — нуль. Detekt,
debug/test APK та обидва app/harness benchmark compile варіанти успішні.
Залежності, правила, golden/baseline і performance budgets незмінні.
Це не нове вимірювання продуктивності. Git також видавав лише
інформаційний CRLF→LF notice для двох відредагованих Kotlin-файлів;
це не compile/runtime failure, налаштування Git не змінювались.

**Mi A2: 26/26 за 100,887 с**, нуль failure/skip статусів. Нові два cases
перевіряють Android SAX (qualified/entity/CDATA values і DTD rejection)
та MIME metadata з misleading display name у реальному HTTP HEAD,
SDK Cast builder і DLNA SOAP. MIME для цього сценарію походить з реального
ContentResolver MP4, display name у descriptor змінений тестом. Це не
окремий custom content provider і не тест фізичного DLNA renderer.

Попередні 24 native scenarios (play/replay/session/seek/proxy/lifecycle/
video-fit) також пройшли. Protected runner відновив debug data й перевірив
три початкові private file hashes. Основна release-версія не змінювалась.
Журнал: `app/build/device-audit/instrumented-78c7a6d428c94a369c6f95c84ee6c1ae.txt`.
Обидва native-протестовані APK збігаються за SHA-256 з фінальними APK
повного gate. Немає заміни після успішного native результату.

## Попередній Cast-блокер закрито окремо

До цих XML/MIME-змін ADB підтвердив unlocked Mi A2. На попередніх Cast APK
повтор пройшов **24/24 за 98,571 с**; три початкові private file hashes
відновлені та збігаються. Цей результат дописаний до Cast lifecycle звіту.
Він не підміняє перевірку нової XML/MIME APK.

## Що ще не підтверджено

Реальні TV / Hisense VIDAA acceptance та «Error de red» потребують окремого
тесту й актуальних логів. Ці правки не є заявленою універсальною відповіддю
на цю помилку. Немає transcoding чи гарантії всіх codec/container комбінацій.
Попередні measured frame-time overruns не закриті. Немає release/version
change, GitHub publication чи shutdown у цьому проході.

Ignored evidence: `build/dlna-*.log`, `build/dlna-*-results/`,
`app/build/device-audit/`. Recovery archives можуть містити private data;
до публікації не включаються.
