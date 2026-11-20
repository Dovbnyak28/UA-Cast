# Cast: ідентичність, завершення та відновлення файла — 9 жовтня 2026

## Межі проходу

Перевірено sender-код для локальних відеофайлів: побудову load payload,
відбір статусів, завершення короткого файла й відновлення тієї самої
SDK-сесії. Це не перевірка кожного Cast/DLNA-приймача чи кожного рядка
репозиторію. Змінено чотири production-файли й три тестові файли.

Дефекти підтверджені негативними host-тестами перед відповідними правками.
Для перших двох: **36 випадків, два failures за 27 с**, решта 34 пройшли.
Для третього, окремо після перших правок: **4 випадки, один failure за 22 с**;
три попередні контролі пройшли. Початкові XML та журнали збережені в ignored
`build/`. Це виконані sender/policy сценарії, не логи збою фізичного TV.

## 1. P2 — старий статус приймається при повторній трансляції того самого файла

Файли: `cast/CastMediaLoader.kt`, `cast/CastSessionRepository.kt`.

Траса: повторний file load → proxy повертає ту саму адресу → MediaInfo
раніше отримувала однаковий `contentId` → expected ID теж був тією самою
адресою → `CastStatusContentPolicy.shouldAccept(old, current)` повертав true.
Ідентифікований пізній FINISHED/ERROR/PLAYING міг міняти стан нового запиту
або скасувати його watchdog. Наявний load-generation guard захищає
PendingResult, але не ці status payload: status порівнювався за content ID.

Виправлення: кожен buffered load має окремий логічний ID з UUID у fragment;
`contentUrl` залишається точно попереднім. Репозиторій очікує фактичний ID
побудованого payload, не адресу, передану в load builder. Live-запити
зберігають і початковий ID, і URL, включно з підписаними query-параметрами.
Ідентичність завантаження не є новим токеном доступу до файла.

Тест `a previous file load status cannot be accepted by a replay of the same
proxy url` до правки падав, після відкидає старий ID і приймає новий; URL
обох payload лишається незмінним. Окремий контроль перевіряє live URL/ID/MIME.

SDK окремо визначає content ID та URL відтворення, тому URL, MIME, session
token, GET/HEAD/Range, proxy routing і ресурсна карта не змінювались.
[Google MediaInfo](https://developers.google.com/android/reference/com/google/android/gms/cast/MediaInfo).

## 2. P2 — перший FINISHED файла обробляється як невдалий live-потік

Файли: `cast/CastMediaStatusMapping.kt`, `cast/CastSessionRepository.kt`.

Траса: load виставляє `selfInitiatedTransition=true` → перший прийнятий
status є IDLE/FINISHED → стара файлова перевірка відхиляє self-initiated
status → recovery policy ігнорує self-event → загальний live reducer
позначає failure, закриває proxy та віддає відтворення телефону. Короткий
файл або перший видимий snapshot уже після EOF є допустимим сценарієм;
це не підтвердження конкретного firmware-дефекту.

Виправлення: після наявних session/content guards FINISHED локального
файла — нормальна кінцева подія незалежно від однорозового self-прапорця.
Вона підтверджує connected/loaded стан і припиняє recovery/watchdogs,
не створюючи live failure чи локального handback. ERROR і transient IDLE
для інших сценаріїв зберігають попередню обробку.

Тест першого FINISHED до правки падав. Перевіряє також LOADED, ownership,
незмінність receiverLoadFailed; null SDK snapshot не є доказом завершення.
Попереднє очікування `false` для self-initiated file FINISHED виправлено:
воно закріплювало неправильний контракт, а не захищало корисну поведінку.
FINISHED у SDK означає завершення відтворення, не відхилення LOAD.
[Google MediaStatus](https://developers.google.com/android/reference/com/google/android/gms/cast/MediaStatus).

## 3. P2 — відновлення тієї самої сесії запускає завершений файл заново

Файли: `cast/CastSuspensionPolicy.kt`, `cast/CastSessionRepository.kt`.

Траса: файл завершився → коротка suspension → та сама SDK-сесія відновилась
з незмінним каналом, matching ID, IDLE/FINISHED → стара canKeepMedia
дозволяла лише PLAYING/PAUSED → startPlayback → новий autoplay load.

Виправлення: policy отримує явну ознаку завершеного buffered item з SDK
mapper. Тільки matching ID + незмінний канал + та сама сесія + file FINISHED
можуть зберегти цей кінцевий стан. Idle live/error, відсутній ID, інший ID
та змінений канал не отримують такого дозволу. Старі pause/playing правила
залишені. Це не обіцянка поведінки при новій сесії чи відсутньому snapshot.

Негативний finished-file resume тест падав до додавання контексту типу
медіа. Очікування retain залишилось; після правки тест явно передає
класифікацію completed file. Контролі stale/missing/switched/idle-live
перевіряють, що нове правило не допускає довільне збереження старого медіа.

## Верифікація

- Після перших правок — 200 host-тестів і focused gate успішні за 2 хв 25 с.
- Після третьої правки — 202 host-тести пройшли, але Detekt відхилив
  complexity 16 за ліміту 14. Цей прогін не зарахований як green.
- Nullable SDK-класифікацію винесено в наявний mapper. Ліміт і baseline не
  змінено. Остаточний focused gate: **203/203**, нуль failures/errors/skips,
  lintDebug, Detekt, debug/test APK — успішно за 2 хв 27 с.
- Усі 59 scoped Android-файлів primary/review checkout збігаються.
- **Повний gate успішний за 8 хв 59 с** (227 завдань: 25 виконано,
  202 up-to-date). **Debug 2710, Release 2316, Play 2316**: нуль
  failures/errors/skips. Незмінні Core 133 результати повторно використано.
- **49 screenshot goldens незмінні**, нуль added/changed; три recorded
  diagnostic background previews у ignored build не є новими еталонами.
  Дванадцять repository checks успішні. Детектори, baseline, goldens,
  залежності та performance budgets не змінені.
- Компілятор без Kotlin warnings; lint Debug/Release/Play має нуль
  errors/warnings, по два наявні Hints щодо API-36 target і Compose BOM.
  Detekt, debug/test APK і компіляція обох app/harness benchmark варіантів
  пройшли. Це не нове вимірювання memory peak чи швидкодії.
- Обидва native-протестовані APK збігаються за SHA-256 з фінальними APK
  повного gate. Частковий результат native не змінює його статус на green.

## Native Mi A2: частковий результат, не green

Перші **5/5 LocalVideo** кейсів пройшли, включно з новим SDK Parcel →
Range тестом. Подальші lifecycle-кейси падали в `setUp`, очікуючи Compose
hierarchy за 30000 мс. ADB підтвердив `secure=true`, `showing=true`,
`mIsShowing=true` і NotificationShade над MainActivity. Це перевірений
keyguard/setup blocker, не доведений збій нового Cast-коду.

Уже невдалий запуск припинено штатним force-stop **лише debug-пакета**, щоб
protected runner виконав finally. Наприкінці instrumentation вивів
`Process crashed` саме після цього примусового завершення; цей рядок не
подається як самовільний production crash. Дані debug-пакета відновлені,
три початкові SHA-256 збігаються. Основна release-версія не змінювалась.

Wake і `wm dismiss-keyguard` виконані на прохання користувача. Запит
прийнятий, але перевірений стан залишився secure/showing: телефон не
розблоковано, PIN/біометрія не обходилися і налаштування захисту не змінені.
**Повний 24-case native результат відсутній**; потрібне фізичне розблокування
і повтор усього прогону. Попередні 23/23 з минулого проходу не сертифікують
цю нову APK. Поточний частковий native-журнал:
`app/build/device-audit/instrumented-d8fff347d8704caca70849900f8efb56.txt`.

### Повтор після фактичного розблокування

У наступному проході ADB підтвердив `showing=false` без будь-якої зміни
налаштувань захисту. На тих самих APK повний повтор пройшов **24/24 за
98,571 с**, нуль failure/skip статусів. Усі три початкові private file hashes
збігаються після відновлення. Поточний Cast native-блокер закрито; попередній
невдалий журнал збережено як окрему подію, не переписано на green.
Успішний native-журнал:
`app/build/device-audit/instrumented-6ea580711cbc45a692805966eaedbf89.txt`.
Приймання на фізичному Cast TV цим повтором не підтверджене.

Новий native кейс перевіряє справжній SDK Parcel round-trip двох load
payload, ідентичність до/після серіалізації та HTTP Range за відновленим
`contentUrl` з реального ContentResolver-файла. Це тест Android SDK-даних
та байтів proxy, не виклик `RemoteMediaClient.load()` на фізичному TV.

## Залишена сумісність і чесні межі

- Payload з явним іншим ID тепер відхиляється навіть для того самого файла.
  Наявний fallback для null/empty IDs не посилювався: без ID цей прохід не
  гарантує виявлення кожного пізнього статусу.
- Реальний приймач має повернути поточний content ID. Android-серіалізація
  не доводить поведінку його firmware чи JSON transport; приймання нового
  ID та повторний cast потрібно перевірити на фізичному приймачі.
- Hisense VIDAA / DLNA «Error de red» не оголошено виправленим цими Cast
  змінами. Немає транскодування чи гарантії всіх контейнерів/кодеків.
- Раніше виміряні frame-time перевищення не закриті. Публікацію GitHub,
  зміну версії й вимкнення комп'ютера цей прохід не виконує.

Ignored evidence: `build/cast-file-*.log`, XML у `build/cast-file-*-results/`
та `app/build/device-audit/`. Приватні recovery-архіви не публікуються.
