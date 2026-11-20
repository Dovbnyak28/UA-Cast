# Стабілізація HTTP cancellation та EPG handoff — 2026-10-03

## Межі перевірки

Перевірено шляхи EPG, update APK та спільного OkHttp helper, а також власність
recovery jobs у плеєрі. Найбільший підтверджений дефект — робота з файлами й
mutable state після завершення coroutine-власника. UI, billing, версії,
залежності, таймаути й формати збережених даних не змінено. Сторонні локальні
зміни збережено. Це цільовий bug hunt, не сертифікація всього репозиторію.

## P1 — скасований HTTP reader переживає власника та його mutex

Файл: `app/src/main/kotlin/com/uacastplayer/core/net/CancellableOkHttpCall.kt`.
Функція: `Call.executeCancellable`.

Шлях: UpdateInstallController → UpdateDownloader.download → downloadMutex →
fetch → executeCancellable → OkHttp callback → copyHashing/publish.

До виправлення `readResponse` виконувався всередині OkHttp callback, а coroutine
в цей час очікувала cancellable continuation. Скасування завершувало очікування
та запускало `fetch.finally`, після чого звільнявся downloadMutex. Але callback
ще міг копіювати bytes, змінювати instance-owned MessageDigest або публікувати
той самий `.apk.part`. `Call.cancel()` не є join callback-а: уже буферизовані
байти, progress callback та файлові операції можуть ще виконуватися.

Тести до виправлення підтвердили:

- Власник уже завершився, хоча його reader було контрольовано зупинено всередині
  response block і він ще не вийшов.
- Відповідь, доставлена після скасування, усе одно запускала retired reader:
  **1 виклик замість 0**. Такий reader може створити файл уже після cleanup.
- Повторний download на тому самому UpdateDownloader та URL працював одночасно
  зі старим writer. Тест із реальними `.part`/`.apk` у Robolectric cacheDir
  отримав **UpdateDownload.Failed замість Ready**. Це перевірка production
  downloader з буферизованими HTTP bodies, не лише mock state machine.

Виправлення: callback передає тільки Response; читання й side effects тепер
виконуються в IO coroutine власника. Вона не звільняє власні mutex/files, доки
reader не вийшов. Job cancellation лишається прив'язаною до Call.cancel під час
body read. Недоставлений Response закривається cancellation handler-ом без
виклику reader. IOException від скасованого socket не перетворюється на
retryable network error. Handler знімається в finally; response закриває use.

Це **не** гарантія миттєвого переривання будь-якого стороннього blocking code.
Reader, який сам завис поза socket і не реагує на скасування, затримає завершення
власника. Звільнити його mutex і дати іншому writer писати паралельно небезпечно.
Socket cancellation перевірено окремо на справжньому localhost ServerSocket.
Цей прогін не доводить пошкодження підписаного APK чи обхід signature gate.

## P2 — готовий EPG-файл губиться під час повернення на caller dispatcher

Файл: `app/src/main/kotlin/com/uacastplayer/data/epg/EpgDownloader.kt`.
Функція: `download`.

Шлях: EpgRepository.load → downloader.download → withContext(IO) → Success(file)
→ повернення на dispatcher власника → repository parse/finally delete.

До виправлення temp file належав attemptOnce до створення Success, після чого
очікувалося, що його отримає EpgRepository і видалить у finally. Але withContext
має cancellable handoff: готовий результат може бути відкинуто, якщо Job
скасовано до обробки dispatch на caller. Attempt уже успішно завершився, а
repository ще не отримав файл; обидва cleanup paths його пропускають.

Детермінований тест зупиняє саме caller dispatcher після завершення download,
перевіряє наявність файла, скасовує Job і тільки тоді виконує delivery. До
виправлення залишався **1 файл замість 0**. Процес не було вбито. Чинний age
sweep міг пізніше видалити його, але до того зайві EPG bytes лишалися в filesDir.

Виправлення: download тримає посилання на unclaimedFile до успішного повернення
withContext. Cancellation на цій межі видаляє файл і поширюється далі. Звичайний
Success не видаляється: його власником, як і раніше, стає caller. Retry policy,
96 MiB cap та stale sweep не змінено.

## Регресійні тести

Додано **10 тестів**:

- `app/src/test/kotlin/com/uacastplayer/core/net/CancellableOkHttpCallTest.kt` —
  7: ownership під час cancellation; late response без reader; successful read;
  reader/header failures; body IO не на caller thread; переривання реального
  socket read зі збереженням CancellationException.
- `app/src/test/kotlin/com/uacastplayer/data/epg/EpgDownloadHandoffTest.kt` —
  2: cleanup скасованого dispatcher handoff і нормальна передача файла caller-у.
- `app/src/test/kotlin/com/uacastplayer/data/update/UpdateDownloadCancellationTest.kt`
  — 1: скасований writer не псує наступний download на тому самому owner/URL;
  перевіряються результат, bytes та відсутність partial files.

Перед production fix: **7 тестів, 4 підтверджених failures**. Усі controls
пройшли. Первинну вимогу object identity для IOException виправлено **до**
production changes: coroutine stack-trace recovery законно копіює виняток;
контрактом є його тип/message, не identity. Це не дефект застосунку.

Before-fix XML збережено локально в ignored build directory:

- `app/build/http-cancellation-stabilization-20261003/before-http.xml`
- `app/build/http-cancellation-stabilization-20261003/before-epg.xml`
- `app/build/http-cancellation-stabilization-20261003/before-apk.xml`

Після fix початкові 7 тестів пройшли. Розширена мережево-продуктова регресія:
**409 тестів / 77 suites, 0 failures/errors/skips**, включно з EPG, APK/update,
DLNA, playlist URL, icons і TS diagnostics. Detekt пройшов; baseline/suppressions
не розширено. Усі 15 наявних `scripts/check-*.sh` пройшли.

## Повний gate

Повний gate: **BUILD SUCCESSFUL in 9m 19s**, 203 tasks (46 executed,
157 up-to-date). Усі три app test tasks виконалися після production changes.

| Перевірка | Результат |
| --- | --- |
| Debug unit | 2 550 tests / 428 suites; 0 failures/errors/skips |
| Release unit | 2 225 tests / 358 suites; 0 failures/errors/skips |
| Play unit | 2 225 tests / 358 suites; 0 failures/errors/skips |
| Core unit | 126 tests / 15 suites; чинні результати без змін у core, Gradle up-to-date |
| Roborazzi | 47 unchanged; 0 changed/added/recorded; goldens не змінено |
| App/core Detekt | 0 findings; baseline 0; suppressions не розширено |
| Debug/Release/Play lint | 0 errors/warnings; у кожному чинний OldTargetApi Hint |
| Assemble | Debug, DebugAndroidTest та підписаний Release успішні |

Збережено наявні дві інформаційні R8 diagnostics про unsupported async parsing
у OrderedClassFileResourceProvider. Нових Kotlin compiler warnings у фінальному
прогоні немає. Тестову зайву Unit-expression прибрано, не додаючи suppression.

Команда відтворення (JDK 21, залежності вже в локальному cache):

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:detekt `
  :core:test :core:detekt :app:verifyRoborazziDebug `
  :app:testReleaseUnitTest :app:testPlayUnitTest `
  :app:lintDebug :app:lintRelease :app:lintPlay `
  :app:assembleRelease '-Puacast.requireSigning=true' `
  --offline --max-workers=1 --no-daemon --continue
```

## Поточний universal APK

- Файл: `app/build/outputs/apk/release/app-universal-release.apk`.
- VersionName **0.9.7**, universal versionCode **164**; minSdk 24 / targetSdk 36.
- Перезібрано **2026-10-03 17:51:15 Europe/Paris**, розмір **24 178 412 bytes**.
- SHA-256: `8366F3825555B1BCECF04E3DBCFE058B5085BCD91731A79D03F76402AB1CE618`.
- `apksigner verify`: успішний, v2/v3 true, 1 signer.
- Certificate SHA-256: `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`.

Це актуальний артефакт після цих виправлень, не APK з попереднього backup/icon
прогону. VersionCode не піднімався: updater не покаже його як новіший реліз
користувачеві, в якого вже встановлено code 164.

## Що не підтверджено цим прогоном

Не виконано hardware QA на Mi A2, Samsung, Xiaomi TV чи Hisense VIDAA. Реальний
install/update dialog, зовнішні сервери IPTV, firmware-specific DLNA playback
та Google Play purchases цим прогоном не перевірялися. Instrumented APK збірка
не дорівнює запуску instrumented тестів на телефоні. Публікацію, version bump,
установлення APK і вимкнення комп'ютера не виконано.
