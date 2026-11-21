# Глибокий дебагінг — 14 вересня 2026

Перевірений commit: `847ffe6e8af356b3b49d2f9f8fcb4ceb78300fe5`, гілка `codex/release-0.9.4`, версія коду 0.9.6.

Це діагностика поточного коду, не звіт про виправлення. Виробничий код, штатні тести, конфігурація збірки та версія не змінювалися. Додано цей звіт і окремий, явно підключуваний набір відтворення під `docs/debug/2026-09-14`.

## Висновок

Підтверджено три проблеми застосунку: блокувальний інсталятор на Main, неправильний rollback гучності DLNA та перенесення паузи між закритою і новою сесіями плеєра. Кожна відтворена окремим тестом на поточних production-класах. Також відтворено дефект fail-open у shell-перевірці detekt baseline.

Окремо повторився нестабільний proxy HTTP-тест. Причина таймауту не доведена; він не зарахований до підтверджених production-багів і не оголошений нешкідливим.

P0 у перевірених шляхах не підтверджено. Перед наступним оновленням насамперед треба виправити D1. Зелені штатні тести не доводять відсутності цих дефектів: три нові інваріанти ними не покриті.

## D1 — інсталятор виконує блокувальну роботу на головному потоці

- **Пріоритет:** P1 / High.
- **Статус:** виконання на Main підтверджене тестом; ANR на фізичному пристрої в цьому сеансі не відтворювався.
- **Файли:** [AppViewModel.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/AppViewModel.kt:183>), [UpdateInstallController.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/UpdateInstallController.kt:92>), [ApkInstaller.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/update/ApkInstaller.kt:108>).
- **Функції:** `downloadAndInstall`, `launchInstall`, `ApkInstaller.install`, `Session.stage`.
- **Тригер:** завантаження оновлення завершене, контролер переходить до встановлення APK.
- **Шлях:** `AppViewModel.viewModelScope` → `scope.launch` → завершення suspending download → `launchInstall` → звичайна синхронна lambda → `ApkInstaller.install`.
- **Очікування:** перевірка архіву, копіювання APK у PackageInstaller session та `fsync` не блокують UI.
- **Фактично:** `install` має тип `(File) -> InstallLaunch`; між ним і Main немає dispatcher boundary. `getPackageArchiveInfo`, `copyTo(out)` та `fsync(out)` виконуються на потоці виклику. IO-контекст завантажувача не переноситься на наступний виклик контролера.
- **Наслідок:** зависання UI під час staging; на повільному або перевантаженому сховищі можливий ANR. Тривалість залежить від APK і пристрою, тому конкретний час зависання не стверджується.
- **Доказ:** `DeepDebugInstallThreadTest` перевіряє фактичний Looper в installer callback при `Dispatchers.Main.immediate`: очікування `false`, результат `true`.
- **Виправлення:** зробити installer boundary suspending і main-safe; перевірку та staging винести на інжектований IO dispatcher. Зберегти single-flight/sessionId guards, коректне завершення/abandon при помилці або скасуванні та окремо перевірити доставку раннього installer outcome після асинхронізації.
- **Регресія:** повільне сховище не блокує UI; повторне натискання не створює другу session; скасування не залишає staged APK без власника.

## D2 — після двох відмов DLNA показується непідтверджена гучність

- **Пріоритет:** P2 / Medium.
- **Статус:** відтворено на реальному `DlnaSessionRepository` з контрольованими SOAP-відповідями.
- **Файли:** [DlnaDeviceSheet.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/dlna/DlnaDeviceSheet.kt:328>), [PlayerDlnaController.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerDlnaController.kt:25>), [DlnaSessionRepository.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/dlna/DlnaSessionRepository.kt:373>).
- **Функція:** `setVolume`.
- **Тригер:** дві зміни гучності до завершення першої мережевої операції; телевізор відмовляє обом.
- **Шлях відтворення:**
  1. `GetVolume` повертає 20.
  2. `setVolume(40)` негайно публікує 40, перший SOAP запит утримується fixture-бар'єром.
  3. `setVolume(80)` зберігає `previous = 40` та публікує 80.
  4. Обидва `SetVolume` отримують HTTP 500.
  5. Перша операція застаріла за generation; друга відкочує UI до свого `previous`, тобто 40.
- **Очікування:** UI повертається до підтверджених 20 або чесно показує невідомий стан до read-back.
- **Фактично:** TV залишається на 20, UI показує 40. Це не лише теоретичне переставлення callback: тест підтверджує дві реальні відмови в HTTP client boundary.
- **Причина:** `_state.volume` одночасно є optimistic UI value і джерелом rollback. Значення `previous` не обов'язково було прийняте телевізором. Read-back після відмови не виконується.
- **Доказ:** `DeepDebugDlnaVolumeTest`: `expected 20, actual 40`.
- **Виправлення:** розділити бажану та останню підтверджену гучність, прив'язати підтверджений стан до renderer/session. При невизначеному результаті виконувати актуальний read-back; generation guard має відкидати старі результати, але не робити optimistic значення підтвердженим.
- **Регресія:** reject/reject, accept/reject, timeout після фактичного прийняття, нормалізація гучності телевізором, disconnect під час read-back.

## D3 — нове відкриття плеєра успадковує паузу закритої сесії

- **Пріоритет:** P2 / Medium.
- **Статус:** відтворено на реальному `PlayerViewModel` та Media3 під Robolectric.
- **Файли:** [PlayerHost.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerHost.kt:83>), [PlayerViewModel.kt — start](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:379>), [releasePlayback](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:809>).
- **Функції:** `togglePlayback`, `releasePlayback`, `start`, `applyChannelSwitch`.
- **Тригер:** відкрити A → пауза → повністю закрити mini-player → вибрати B.
- **Очікування:** новий явний запит відтворення запускає B. Повторне приєднання того самого request після rotation, навпаки, має зберігати паузу.
- **Фактично:** `releasePlayback` очищує media items і session state, але не змінює `playWhenReady=false`. Activity-scoped ViewModel залишається живим. Новий `start` готує B без нового наміру play, тому B залишається на паузі; потрібне додаткове натискання Play.
- **Доказ:** `DeepDebugPlayerReopenTest`: поточний канал уже B, але `playWhenReady` усе ще `false`.
- **Виправлення:** явно відрізняти нову playback session від reattach/rotation; ініціалізувати autoplay intent для нового запиту, не руйнуючи збережену паузу під час reattach та не запускаючи локальне відтворення під час remote ownership.
- **Регресія:** paused close/reopen; paused rotation; collapse/expand; новий запит після sleep timer; reopen під час Cast/DLNA.

## D4 — baseline gate помилково проходить без shell-залежностей

- **Пріоритет:** P2 / Medium для надійності перевірок; не runtime-дефект APK.
- **Статус:** відтворено окремою командою з fixture XML, що містить один `<ID>`.
- **Файл:** [check-detekt-baseline.sh](<C:/UA CAST/scripts/check-detekt-baseline.sh:18>).
- **Тригер:** скрипт запускається з PATH, у якому немає `grep`, `wc` або `tr`.
- **Очікування:** помилка інструментарію завершує gate ненульовим кодом; жодного повідомлення OK.
- **Фактично:** присвоєння через `readonly ISSUE_COUNT="$(...)"` маскує помилку pipeline. Порожній count викликає помилку числового порівняння всередині `if`, потім друкується `OK`, exit code 0.
- **Доказ:** той самий fixture з одним порушенням: відсутні утиліти → exit 0; утиліти доступні → exit 1. Поточний реальний baseline порожній; прихованих `<ID>` у ньому цим тестом не виявлено.
- **Виправлення:** перевірити залежності до обчислення, окремо перевіряти статус і числовий результат підрахунку; розрізняти grep no-match і помилку читання. Додати tests missing-tool/unreadable/empty/nonempty baseline.
- **Межа:** цей тест не доводить обходу поточного GitHub Ubuntu job, де утиліти наявні. Він доводить некоректний результат самого gate в неповному середовищі.

## O1 — відкритий proxy timeout, причина не встановлена

Під час спільного запуску JVM-тестів і lint впав `ProxyManifestLeaseTest.all 1000 emitted segment URLs and root remain fetchable over HTTP` на першому root response. Клієнт отримав 65 481 байт і дочекався 5-секундного socket timeout. У thread dump обидва captured pool workers чекали нових задач, accept-thread чекав нового socket. Це не доказ deadlock у handler; одночасно цього недостатньо, щоб звинуватити тільки навантаження ОС.

Окремий повтор усього `ProxyManifestLeaseTest` без зміни коду/timeout пройшов: 4 tests, 0 failures. Аналогічний випадок уже зафіксовано у [верифікації 6 вересня](<C:/UA CAST/docs/REPEAT_DEEP_AUDIT_FIX_VERIFICATION_2026-09-06.md:118>).

Збережено XML з повним стеком: [proxy failure](<C:/UA CAST/build/deep-debug-20260914/parallel-suite-results/TEST-com.uacastplayer.data.cast.ProxyManifestLeaseTest.xml>).

Подальша діагностика: phase timestamps accept/admit/write/flush/close, кількість записаних/прочитаних байтів, frame-aware HTTP client у контрольному тесті та повтор на Linux/Android з тим самим payload. Не підвищувати timeout і не робити автоматичний retry замість встановлення причини. Саме цей запис не доводить причину `Error de red` на Hisense VIDAA.

## Перевірки та відтворення

Переглянуто наскрізні шляхи player ownership/lifecycle/retry, Cast session identity і handback, DLNA connect/stop/volume, update download/install/outcome, proxy sockets/remux/manifests, EPG mutation/cancellation, icon worker pool, playlist persistence. Це не ручний UI-тест кожного екрана і не апаратна перевірка телевізора.

Прогони:

- Початковий виклик Gradle використав up-to-date результати; він не зараховується як новий прогін.
- Примусовий повний прогін з двома новими діагностичними тестами: core 119/119 pass; app 2069 tests, 2 failures — лише нові D2/D3. Усі 2067 штатних app tests пройшли.
- Ізольований набір D1/D2/D3: 3 tests, 3 очікувані failures. Повторено після перенесення fixture у постійний каталог — знову 3/3 failures з тими самими причинами.
- Повний штатний прогін одночасно з lint: app 2067 tests, 1 failure O1; core 119/119 pass.
- Ізольований `ProxyManifestLeaseTest`: 4/4 pass.
- `lintDebug`, `lintRelease`, `lintPlay`: задачі успішні. Інформаційні Hint не прирівнюються до errors або до підтверджених вимог Google Play.
- `:core:detekt`, `:app:detekt`: успішні, використано cache/up-to-date для незміненого коду.
- Architecture boundaries і реальний detekt baseline: PASS після запуску з коректним Git Bash PATH. Перший baseline OK без утиліт був хибним — див. D4.

Фінальний примусовий повтор штатних JVM-тестів без паралельного lint: **BUILD SUCCESSFUL**, 1 хв 23 с; core **119/119**, app **2067/2067** — разом **2186 tests, 0 failures, 0 errors, 0 skipped**. Попередній O1 timeout залишається в цьому звіті: успішний повтор не встановлює його причину.

Запуск збережених діагностичних тестів з кореня проєкту:

```powershell
.\gradlew.bat -I docs/debug/2026-09-14/repro.gradle :app:testDebugUnitTest --tests '*DeepDebug*' --rerun --offline --console=plain --no-configuration-cache
```

На перевіреному commit команда має падати на трьох зазначених інваріантах. Це не зміна штатного gate: без `-I` ці файли не входять у source sets. [Gradle fixture](<C:/UA CAST/docs/debug/2026-09-14/repro.gradle>), [тест D1](<C:/UA CAST/docs/debug/2026-09-14/tests/com/uacastplayer/app/DeepDebugInstallThreadTest.kt>), [тест D2](<C:/UA CAST/docs/debug/2026-09-14/tests/com/uacastplayer/dlna/DeepDebugDlnaVolumeTest.kt>), [тест D3](<C:/UA CAST/docs/debug/2026-09-14/tests/com/uacastplayer/player/DeepDebugPlayerReopenTest.kt>).

XML результатів збережено під `build/deep-debug-20260914/`; `clean` може видалити ці generated artifacts. Вихідні діагностичні тести та цей звіт зберігаються у `docs`.

## Відхилені підозри та наявні захисти

- Старі DLNA connect results не публікують довільно нову сесію: наявні generation checks, serializer і cancellation checkpoints. D2 — конкретна помилка rollback, а не відсутність усієї синхронізації.
- Cast callbacks мають перевірки identity сесії та contentId; повторна реєстрація callback передбачає unregister. Загальне твердження «кожен reconnect дублює listeners» не підтвердилося.
- Delayed channel switch скасовується при застосуванні нового списку/каналу; retry/stall jobs мають окремі cancellation paths. Старий pending-switch дефект не подається повторно як новий.
- Закриття PlayerHost звільняє media items, а onCleared звільняє MediaSession/player; сам факт Activity-scoped ExoPlayer не доводить leak.
- Proxy відстежує активні sockets та закриває їх при stop; registry відхиляє створення remux після зупинки. O1 не доводить socket leak.
- EPG write coordinator та playlist persistence вже містять захист від застарілих записів; icon prefetch використовує bounded worker pool, а не coroutine на кожен канал.
- APK gate перевіряє package name і підпис. D1 не означає, що встановлення непідписаного стороннього APK дозволене.

## Порядок виправлення

1. D1: main-safe installer boundary, cancellation/session-result regressions, вимірювання UI responsiveness на слабкому телефоні.
2. D2: confirmed/requested volume state та adverse SOAP tests.
3. D3: чітка семантика fresh request проти retained reattach, перевірка rotation і remote ownership.
4. D4: fail-closed shell gate з негативними fixtures.
5. O1: цільовий stress/capture на кількох ОС; після встановлення причини — виправлення та стабільний regression test.

Не виконувалося цього разу: встановлення APK на телефони, апаратний Cast/Hisense VIDAA тест, Play Console/billing purchase, публікація релізу. Твердження «все виправлено» або «проблему телевізора закрито» ці результати не обґрунтовують.
