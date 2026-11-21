# Пошук багів та перевірка виправлень — 2026-09-06

Продовження [emulator-аудиту](<C:/UA CAST/docs/EMULATOR_VERIFICATION_2026-09-06.md>).
У цьому проході виправлено **три підтверджені P2**, включно з відкритим V1.
Нових P0/P1 у перевірених сценаріях не підтверджено. Це не твердження про
відсутність усіх можливих багів у застосунку.

## Підтвердження та виправлення

### B1 / V1 / P2 — невидимі закінчення назв каналів

- **Шлях:** inline player → `NextChannelsRail` → однорядковий `Text` шириною 96 dp.
- **До:** при 200% шрифті `Channel 2` і `Channel 3` обидва відображалися як
  `Channel`. Довгі назви обрізалися без багатокрапки також при звичайному шрифті.
- **Виправлення:** дворядкова область підпису з `TextOverflow.Ellipsis`.
  Шрифт не зменшено, системний font scaling не вимкнено. Картки залишаються
  обмеженої ширини; довгі назви не роздувають горизонтальний список.
- **Тести:** `NextChannelsLayoutTest`, 100% / 150% / 200%, EN/UK/ES назви,
  перевірка `TextLayoutResult`, видимості суфікса, явного ellipsis і правильного
  playback index при виборі. Android-тест повторює перевірку коротких назв на ART.

[Реалізація](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerInfoPanel.kt:293>) ·
[До — 200%](<C:/UA CAST/build/emulator-api36-large-font-artifacts/player-error-actions.png>) ·
[Після — 200%](<C:/UA CAST/build/bughunt-api36-large-font-artifacts/player-next-channel-names.png>)

### B2 / P2 — auto-PiP переживав розгорнутий екран плеєра

- **Шлях:** fullscreen playback → `PlayerScreenEffects.syncParams(autoEnter=true)` →
  Back/collapse → `PlayerHost` прибирає `PlayerScreen`, але Activity залишається.
- **До:** dispose відновлював орієнтацію, brightness та keep-screen-on, але не
  відкликав Activity-level auto-PiP. Отже наступний Home міг використати старий
  дозвіл автоматичного переходу в PiP вже з іншого екрана.
- **Доказ:** тест, який прибирає ефекти плеєра з composition, залишаючи Activity,
  падав на перевірці `autoEnterEnabled == false`. Пауза/відновлення перед dispose
  працювали; дефект був саме у відсутньому cleanup.
- **Виправлення:** `PipController.disableAutoEnter()` викликається при dispose.
  Воно змінює тільки auto-enter flag, має API 31 guard та наявну nonfatal boundary.
  Наступне відкриття fullscreen знову публікує актуальні параметри.
- **Межа доказу:** host-тест записує виклики Activity API; він не імітує справжнє
  системне плаваюче вікно чи декодування live IPTV. Реальний Home/PiP cycle із
  валідним потоком на телефоні залишається hardware acceptance check.

[Cleanup](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerScreenState.kt:118>) ·
[Platform adapter](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerGestureLayer.kt:267>) ·
[Regression](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/ui/player/PlayerPipOwnershipTest.kt>)

### B3 / P2 — «Зменшити гучність» могло її підвищити

- **Шлях:** відкриття плеєра з гучністю 10/15 → системна зміна до 3/15 →
  натискання «−» у levels sheet → обчислення від старої UI-копії 10/15.
- **До:** замість зменшення реальної гучності дія повертала значно голосніше
  значення. При відкритті sheet індикатор теж міг показувати snapshot відкриття плеєра.
- **Виправлення:** кожен крок читає поточне значення Android, змінює його на один
  реальний volume step і читає результат назад. Збережено обробку відмови DND.
  Sheet синхронізується при відкритті; gesture accumulator перечитується при
  початку, завершенні й скасуванні жесту, зберігаючи дробовий рух усередині drag.
- **Тести:** stale platform volume → decrease, відкриття зі зміненим volume,
  кроки біля 0/max і readback. Android-тест змінює stream через AudioManager,
  натискає справжню UI-кнопку та перевіряє 3 → 2; початкову гучність відновлює
  у `finally`. DND exception tests продовжують проходити.
- **Обсяг:** оновлення на відкритті та взаємодії, без постійного background
  polling чи нового глобального listener. Hardware/route stress на фізичному
  пристрої не підміняється емулятором.

[Stepper adapter](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerGestureLayer.kt:88>) ·
[Sheet](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerLevelsSheet.kt:42>) ·
[Gesture boundaries](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerFullscreenContent.kt:90>) ·
[Regression](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/ui/player/PlayerVolumeOwnershipTest.kt>)

## Перевірки

| Перевірка | Фінальний результат |
|---|---|
| App unit / Compose / proxy / screenshot verification | **2052 tests, 341 suites**, 0 failures/errors/skips |
| Core, примусово перевиконано | **114 tests, 13 suites**, 0 failures/errors/skips |
| Разом host | **2166 tests, 354 suites**; +10 tests проти попереднього проходу |
| Android 16 / API 36 / x86_64 / 4 KB | **79 pass, 2 skip, 0 fail**, 137.721 s |
| Android 17 / API 37 / x86_64 / 16 KB | **79 pass, 2 skip, 0 fail**, 109.341 s |
| Android 16 UI: системний шрифт 200%, animation scales = 1 | **8 pass, 0 skip, 0 fail**, 39.564 s |
| App + core Detekt | PASS; baseline порожній |
| Play lint | **0 errors, 0 warnings, 3 hints** |
| Debug APK / Android test APK / Play AAB | Зібрані; publish не виконувався |
| 14 repository guards | PASS; включно з architecture, Cast icon placement, Play distribution |
| `git diff --check` | PASS; лише попередні CRLF/LF notices Git |

Android counts — окремі status codes, не лише `OK (81 tests)`. Два skip на кожній
VM — opt-in приватний плейлист у `TemporaryPlaylistInstrumentedTest`, не нові
volume/name regressions. Додаткові 8 UI tests — повтор підмножини, не нові унікальні тести.
Повні VM-прогони: font 100%, анімації вимкнено. Обидві VM: 2 GB RAM, WHPX,
SwiftShader, 1280 × 2856 px / density 480. Це функціональні debug-тести, не release benchmarks.

У зібраних crash buffers обох VM немає записів; у зібраних events logs немає
`am_crash` / `am_anr`. Це обмежене спостереження за прогонами, не доказ відсутності
всіх leaks/ANR. Валідація реального відеодекодера чи receiver тут не проводилась.

Логи:

- [Доведення дефектів до production fix](<C:/UA CAST/build/bughunt-red-confirmation.log>) — 7 failures у 9 адресних tests.
- [Host app / screenshot run](<C:/UA CAST/build/bughunt-final-gate.log>) — app suite PASS; загальна команда тоді впала лише через довжину рядка у test fixture.
- [Fresh core](<C:/UA CAST/build/bughunt-core-fresh.log>)
- [Фінальний quality/package gate після виправлення рядка](<C:/UA CAST/build/bughunt-final-quality.log>) — BUILD SUCCESSFUL.
- [Android 16](<C:/UA CAST/build/bughunt-api36-full-tests.log>) · [Android 17](<C:/UA CAST/build/bughunt-api37-full-tests.log>)
- [Android 16 / 200% UI](<C:/UA CAST/build/bughunt-api36-large-font-tests.log>) · [14 guards](<C:/UA CAST/build/bughunt-guards.log>)

### Проміжні результати не приховано

- Перші нові fixtures потребували корекції: доступ до PiP через recording Activity,
  використання доступного offline Robolectric SDK та явна прокрутка до ще не
  composed LazyRow item. Це помилки fixtures, не production findings.
- Перший повний suite: 2052 tests, один fail у старому volume UI test із
  `AudioManager = null`. Fixture переведено на реальний Robolectric AudioManager:
  залишено перевірку доступності кнопок при 200%, додано assertion platform volume
  та відповідності UI readback. Наступний повний suite — усі 2052 PASS.
- Detekt виявив один надто довгий рядок після розширення fixture. Рядок перенесено;
  повторний quality gate пройшов. Rules/suppressions/baseline не послаблювалися.
- Goldens не перезаписувалися. Два старі R8 повідомлення про async parsing provider
  залишаються в packaging log; їх не масковано як виправлені application warnings.

## Артефакти

Актуальний emulator-tested APK — **universal**, не старий `app-debug.apk` з попереднього проходу.
Версія без змін: **0.9.3**, universal versionCode **124**.

- [Debug universal APK](<C:/UA CAST/app/build/outputs/apk/debug/app-universal-debug.apk>)
  SHA-256: `5322E3808FB7A2268C430678F6F864A89150E61B65768EA3FFD797304395AE8B`.
- [Android test APK](<C:/UA CAST/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk>)
  SHA-256: `5128F267AD9D00854EB8750E79A22EA36958207CA0CA3B9A44D79D8C06552F40`.
- [Play AAB](<C:/UA CAST/app/build/outputs/bundle/play/app-play.aab>)
  SHA-256: `E40B8A85689281516041F6611BB31974F160CDB3C2ACF90C90FE89B043E2E9A3`.

## ARCHITECTURE CHECK — після змін

- П'ять production-файлів змінено локально; кожен зберіг свою відповідальність:
  розмітка, window effects, platform gesture/volume adapter, sheet, gesture input.
- UI не отримав networking, persistence, Media3 ownership чи recovery subsystem.
  Нових singleton, scopes, background workers, runtime classes або дубльованої
  state machine немає; напрямок залежностей не змінено.
- Activity-level PiP cleanup прив'язано до того самого власника, який його вмикає.
  Rotation очищає стару Activity; нова composition публікує свої актуальні params.
- AudioManager залишається джерелом реальної гучності. Дробовий gesture accumulator
  — лише UI intent на час drag, не новий global volume owner.
- Тести політик/розмітки не потребують TV, мережевого IPTV чи приватних даних.
  Новий recording Activity існує лише в host tests; production API заради нього не змінювався.
- Не створено нових архітектурних шарів чи hotspot. Попередні зміни dirty worktree збережено.

## Що далі

Підтверджені B1–B3 цього проходу закриті. Залишаються acceptance-перевірки:
live IPTV/апаратні codecs та справжній Home/PiP/audio-route cycle на телефоні,
Chromecast/DLNA/Hisense VIDAA з реальним receiver, Play Billing/Console, ARM і старі
Android API, release-like peak heap/frame/GC профілювання на слабких пристроях.
Ці обмеження не оголошуються знайденими crash-багами, але й не вважаються перевіреними.

**Вердикт:** виправлення пройшли host та Android 16/17 регресії; можна продовжувати
контрольоване internal testing. Гарантії «все ідеально» чи дозволу на широкий
production rollout цей прохід не дає. Тестові VM зупинено; фізичні телефони,
основну встановлену версію та її дані не змінювали. Publish/push і вимкнення
комп'ютера не виконувалися.
