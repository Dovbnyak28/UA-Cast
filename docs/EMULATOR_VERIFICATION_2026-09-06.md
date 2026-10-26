# Android emulator verification — 2026-09-06

Оновлення після цього проходу: V1 виправлено та повторно перевірено; також закрито
PiP/volume defects. Актуальні результати — у [звіті виправлень](<C:/UA CAST/docs/BUGHUNT_FIX_VERIFICATION_2026-09-06.md>).
Наведені нижче результати залишено як історичний стан до цих змін.

Перевірено поточний debug APK після [виправлень повторного аудиту](<C:/UA CAST/docs/REPEAT_DEEP_AUDIT_FIX_VERIFICATION_2026-09-06.md>).
Це реальні Android instrumented runs на ART, не лише Robolectric на хості.

Результат: фінальні автоматизовані прогони без failures; візуальний перегляд виявив
**один підтверджений відкритий P2** з обрізанням назв каналів при великому шрифті.
Зелений runner не означає, що всі екрани і всі production-сценарії перевірено.

## Середовища та результати

| Середовище | Налаштування | Pass | Skip | Fail | Час runner |
|---|---|---:|---:|---:|---:|
| Android 17 / API 37, x86_64, 16 KB pages | Pixel_10_Pro, шрифт 100%, анімації вимкнено | 77 | 2 | 0 | 100.743 s |
| Android 16 / API 36, x86_64, 4 KB pages | Ізольований AVD, шрифт 100%, анімації вимкнено | 77 | 2 | 0 | 101.990 s |
| Android 16 / API 36, додатковий UI run | Системний `font_scale=2.0`, усі три animation scales = 1 | 6 | 0 | 0 | 33.241 s |

Два повні прогони містять той самий набір 79 тестів; додаткові шість — повтор
`UiPolishInstrumentedTest` в іншій конфігурації, а не шість нових унікальних тестів.
Попередній ознайомчий API 37 run до додавання нових тестів також пройшов:
73 pass / 2 skip / 0 fail, 103.683 s; він не підміняє фінальний результат.

Обидва AVD: 2 GB RAM, 4 virtual CPU, WHPX, software GPU SwiftShader,
1280 × 2856 px, density 480. Фактичний `Runtime.maxMemory()` застосунку — 192 MiB.
API 36 image: `android-36.1/google_apis_playstore/x86_64`; API 37 image:
`android-37.1/google_apis_playstore_ps16k/x86_64`. Значення API/Android/page size
перевірено через `getprop` та `getconf`, а не виведено лише з назви AVD.

Логи:

- [API 37 — фінальні 79 tests](<C:/UA CAST/build/emulator-api37-final-tests.log>)
- [API 36 — фінальні 79 tests](<C:/UA CAST/build/emulator-api36-full-tests.log>)
- [API 36 — 200% font / animation UI tests](<C:/UA CAST/build/emulator-api36-large-font-ui-tests.log>)

Pass/skip/error пораховано за окремими `INSTRUMENTATION_STATUS_CODE`, а не за
exit code ADB чи лише `OK (79 tests)`. Два `-4` — `AssumptionViolatedException`
у `TemporaryPlaylistInstrumentedTest.importOptInFixture`: приватний зовнішній
плейлист не ввімкнено. Пропущені `sampleStreamsRenderVideoAndLifecycleRemainsCorrect`
та `inspectSelectedManifestsWithoutPlayback`; вони **не** зараховані як pass.
Фінальний `INSTRUMENTATION_CODE: -1` — штатне завершення runner, не помилка тесту.

## Що перевірено

- MediaSession: реальні Android `MediaController`/`MediaSession`/ExoPlayer,
  усі чотири Next/Previous команди для singleton timeline; відкликання Play/Prepare
  після зміни ownership; повернення локального керування та зміна navigation boundaries.
- Player lifecycle: 14 сценаріїв — 30 play/pause toggles, 10 перемикань каналів,
  10 open/close та mini/fullscreen циклів, rotation/recreation, останній вибраний канал,
  background/foreground intent, sleep expiry, явна пауза, close/release, remote ownership.
  Лічильник перевіряє єдиний live PlayerViewModel; це не повний heap leak detector.
- Proxy: 17 loopback tests — range, redirect, HEAD, unsupported methods, stale session
  URL, missing resources, parallel requests, rewrite, remux та flatten.
- DLNA: 6 loopback tests з fake renderer — SOAP faults, unreachable renderer,
  SetURI/Play ordering та RenderingControl. Справжній телевізор не підключався.
- Parser/storage: hostile HLS/M3U/XMLTV, повторні URI/aliases, snapshot open/commit
  failures на Android filesystem, empty playlist, синтетичні великі плейлисти.
- UI: пошук налаштувань і перехід до секції, видимість Wi-Fi toggle над mini-player,
  channel picker, device picker, кнопки яскравості, recovery actions, PIN retry,
  повернення з Help. Fullscreen controls також знято окремою fixture при 100%/200%.
- Update/install chain: 8 instrumented tests на контракти поточного test suite;
  це не end-to-end публікація або встановлення оновлення з Google Play.

Нові тести цього проходу:

1. [MediaSessionRuntimeInstrumentedTest.kt](<C:/UA CAST/app/src/androidTest/kotlin/com/uacastplayer/player/MediaSessionRuntimeInstrumentedTest.kt>) — три runtime-тести adapter/controller.
2. [AuditRegressionInstrumentedTest.kt](<C:/UA CAST/app/src/androidTest/kotlin/com/uacastplayer/playlist/AuditRegressionInstrumentedTest.kt>) — раннє відхилення HLS з 2 000 000 URI до unwrap/parser amplification; здоровий parse після відмови.

## Підтверджена відкрита проблема V1 / P2

**Файл/функція:** [PlayerInfoPanel.kt:293](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerInfoPanel.kt:293>), `NextChannelsRail`.

**Відтворення:** Android 16, системний шрифт 200%, inline player, наступні канали
`Channel 2` та `Channel 3`. В обох картках видимий підпис стає `Channel`, без
багатокрапки. Повні назви є в sheet «View all», вибір каналу працює.

**Доказ у коді:** `maxLines = 1`, фіксована `.width(96.dp)`, без явного `overflow`.
При збільшенні шрифту назва не вміщується і кліпується. Це погіршує розрізнення
каналів без логотипів; не crash і не неправильний playback index.

**Очікування:** зрозумілий підпис/ознака скорочення, адаптація картки до великого
шрифту, доступ до повної назви.

**Рекомендоване виправлення:** bounded adaptive width та/або дворядковий підпис,
явний `TextOverflow.Ellipsis`, без вимкнення системного font scaling. Додати
layout regression для схожих назв при 100%/150%/200% та довгих локалізованих назвах.
Поточні UI-тести перевіряють actions/semantics, але не повноту намальованого тексту;
тому можуть бути зеленими при цьому дефекті.

**Статус:** виявлено в межах запиту на тестування, production-код у цьому проході
не змінено. Недолік не приховано за загальним PASS.

[Скриншот V1 — 200%](<C:/UA CAST/build/emulator-api36-large-font-artifacts/player-error-actions.png>) ·
[Ті самі картки — 100%](<C:/UA CAST/build/emulator-api36-artifacts/ui-polish/player-error-actions.png>) ·
[Повні назви у channel picker](<C:/UA CAST/build/emulator-api36-large-font-artifacts/player-channel-picker.png>)

## Візуальні докази

Вручну переглянуто знімки recovery screen, налаштувань, device/channel picker,
brightness controls та landscape overlay. При 200% recovery actions доступні;
Wi-Fi control досяжний і не перекритий mini-player; channel picker показує повні
тестові назви. Це вибіркова перевірка, не сертифікація кожної локалі/розміру екрана.

- [Налаштування Wi-Fi, 200%](<C:/UA CAST/build/emulator-api36-large-font-artifacts/settings-wifi-target.png>)
- [Playback settings, 200%](<C:/UA CAST/build/emulator-api36-large-font-artifacts/settings-playback.png>)
- [Device picker, 200%](<C:/UA CAST/build/emulator-api36-large-font-artifacts/player-device-picker.png>)
- [Brightness/volume, 200%](<C:/UA CAST/build/emulator-api36-large-font-artifacts/player-level-controls.png>)
- [Landscape overlay, 200% fixture](<C:/UA CAST/build/emulator-api36-artifacts/player-controls-audit/fullscreen-200-timer.png>)

`UiAutomation` window screenshots можуть містити кадр переходу/IME; assertions
перевіряють destination окремо. Fullscreen fixture синхронізує capture з Compose root.
Вимір visual bounds іконки не є виміром розширеної touch-target області Compose.

## Пам'ять, швидкодія та діагностика

| Синтетична операція | API 36 | API 37 |
|---|---|---|
| HLS 2 000 000 URI: unwrap/parser rejection + healthy parse | 21 ms | 52 ms |
| 40 000 M3U каналів: parse + group, три samples | 341 / 340 / 332 ms | 456 / 390 / 379 ms |

Це debug ART у віртуальній машині, **не Macrobenchmark release build**, не оцінка
слабкого телефона та не peak heap. HLS measurement виключає побудову input.
Delta `art.gc.bytes-allocated` — process-wide приблизна метрика: API 36 показав 0,
API 37 — 98 304 bytes; значення 0 не доводить відсутність алокацій. Збережено сирі
samples, GC time і умови, без заяв про «нуль алокацій» чи гарантований FPS.

[API 36 parser samples](<C:/UA CAST/build/emulator-api36-artifacts/player-controls-audit/playlist-device-measurement.txt>) ·
[API 37 parser samples](<C:/UA CAST/build/emulator-api37-artifacts/files/player-controls-audit/playlist-device-measurement.txt>) ·
[API 36 hostile HLS](<C:/UA CAST/build/emulator-api36-artifacts/player-controls-audit/hls-unwrap-structural-budget.txt>)

Crash buffers обох VM порожні. В API 36 events є ANR системного
`com.google.android.gms.persistent` під час `DOMAINS_NEED_VERIFICATION`; це
не ANR `com.uacastplayer.debug`. У зібраному events log немає app crash/ANR,
а app exit history показує лише штатний FORCE STOP після завершення instrumentation.
Це спостереження за цими прогонами, не гарантія відсутності ANR за будь-якого навантаження.

[API 36 exit info](<C:/UA CAST/build/emulator-api36-exit-info.txt>) ·
[API 36 events](<C:/UA CAST/build/emulator-api36-events.log>)

## Build, ізоляція та ARCHITECTURE CHECK після змін

- `:app:assembleDebugAndroidTest :app:detekt --continue --offline --no-daemon`:
  **BUILD SUCCESSFUL**, 18 s. Architecture guard, empty baseline guard,
  `git diff --check` пройшли; для останнього лишилися лише Git CRLF/LF notices
  щодо раніше змінених файлів. Нових suppressions немає.
- Додано лише Android-тести; не змінено production ownership, шари, залежності,
  lifecycle scopes чи state machines. Тестові Player/Session/Controller звільняються
  в `@After`; синтетичні inputs не залежать від приватного плейлиста або TV.
- APK `com.uacastplayer.debug`, версія **0.9.3 / 124**, SHA-256
  `1B35DFEC11AAB969AB1E1C9B892FD90F2488FCA70C09CB5C7A914E84A0CFACA0`.
  Production APK незмінний від попереднього gate; перебудовано test APK.
- 2156 host tests з попереднього звіту тут повторно не запускалися; emulator counts
  не додаються до них як кількість нових унікальних тестів.
- Фізичні телефони не використовувалися. Existing API 37 AVD запускався read-only,
  без збереження snapshot. API 36 AVD створено окремо під ignored `build/emulator-avds`.
  Результати скопійовано до `build`; обом тестовим VM надіслано штатний `emu kill`.
  Основний застосунок, приватні плейлисти та дані телефонів не змінювалися.

[Build log](<C:/UA CAST/build/emulator-runtime-tests-build.log>)

## Залишкові перевірки та вердикт

1. V1/P2: адаптація назв у `NextChannelsRail` до великого шрифту + regression.
2. Справжнє декодування live IPTV, hardware codecs/DRM/audio focus/headset/PiP на
   фізичному Android. Основна lifecycle fixture використовує синтетичний HLS без
   валідного відео; її PASS не означає, що реальний відеопотік відтворювався.
3. Chromecast/DLNA/Hisense VIDAA з фізичним receiver і Wi-Fi disconnect/reconnect.
4. Play Billing, підписаний release artifact та Play Console checks; target store
   чи billing configuration не змінено, CI remote run не запускався.
5. Старі Android API 24/30 та ARM ABI: у цьому проході ці середовища не запускалися.
6. Release-like frame/GC/peak-heap benchmarks на слабких телефонах, TalkBack та
   ширша матриця локалей/екранів. 16 KB x86_64 PASS не сертифікує всі native ARM libs.

**Вердикт:** перевірені Android 16/17 runtime-сценарії пройшли; нових P0/P1 у цих
тестах не підтверджено. Є відкритий візуальний P2. Контрольоване internal testing
можна продовжувати; твердження «все виправлено» або готовність до широкого rollout
цими результатами не доведені. Комп'ютер не вимикався; publish/push не виконувалися.
