# Завершення та повторний запуск відеофайла — 9 жовтня 2026

## Підтверджений баг: P2

**Сценарій:** локальний відеофайл доходить до кінця. Користувач хоче
переглянути його знову кнопкою плеєра або командою MediaSession.

**Фактична поведінка до виправлення:** Media3 переходив у `STATE_ENDED`,
але `playWhenReady` залишався `true`. UI обчислював `wantsToPlay` з цього
значення і показував «Паузу». Перше натискання тільки скидало прапорець;
подальший Play не повертав позицію на початок. MediaSession також не
запускала повторне відтворення. Відкрити файл заново було обхідним шляхом.

**Очікування:** завершений файл залишається на останньому кадрі, не
запускається сам після background/foreground, а один явний Play починає
його спочатку. Це не нова поведінка для нескінченних IPTV-каналів.

Траса: Media3 END → `PlayerEventListener.onPlaybackStateChanged()` →
ViewModel playback intent → `syncPlaybackControls()` / SessionPlayer → UI.
Раніше END не мав дії, яка завершувала намір локального файлового відтворення.

## Доказ до зміни production-коду

На Mi A2 / Android 11 використано синтетичний короткий MP4. Тестова збірка
пройшла assembly, lintDebug і Detekt за 48 с. Негативний native-прогін:
**4 тести, 2 failures за 17,913 с**. Два попередні контрольні кейси пройшли.

- `naturalCompletionOffersReplayAndRestartsOnOneTap`: assertion
  `A completed file must offer Play, not Pause`.
- `aCompletedFileCanBeReplayedThroughTheMediaSession`: після одноразового
  `MediaSession.player.play()` `isPlaying` не з'явився за 5000 мс.

Це помилки після фактичного завершення Media3, не збій генератора, відсутній
декодер або недоступна мережа. Runner правильно повернув failure. Дані
debug-пакета відновлені, три початкові SHA-256 збігаються.

## Мінімальне виправлення

Змінено лише два production-файли: `player/PlayerEventListener.kt` та
`player/PlayerViewModel.kt`.

- Listener передає END власнику через окремий hook, не створюючи іншого
  player, coroutine чи нового власника стану.
- Власник перевіряє саме поточний engine, локальну файлову метаінформацію
  та відсутність віддаленого власника. Тоді скидає відкладене фонове resume
  і викликає pause, не очищаючи item чи останній кадр.
- Явний Play на завершеному локальному файлі робить seek до default/start
  і запускає ту саму Media3-інстанцію. UI й MediaSession мають єдиний шлях
  виконання команди. IPTV END/recovery та Cast/DLNA-відтворення не змінені.
- Перевірка актуального engine захищає від інжектованого старого END під
  час завантаження заміни. Це окремий захисний тест, а не заявлений новий
  відтворений баг конкретної версії Cast SDK.

Тести доповнено без послаблення assertions: END-hook лише для ENDED;
захист playback intent при старому callback; реальне повторне відтворення
кнопкою та через MediaSession; completed → background → foreground без
самовільного повтору. Системний кейс перевіряє MediaSession adapter, а не
фізичний Bluetooth-пульт або Binder-команду зовнішнього контролера.

## Перевірки після зміни

Перший focused gate: **15/15 host-тестів**, lintDebug, Detekt, debug/test
APK — успішно за 2 хв 20 с. У ньому чотири suites: event listener,
fresh request, live-window lifecycle, local-file failure. Системний
MediaSession controller suite перевіряється у наступному повному gate.

Після додавання захисного тесту всі **57** scoped Android-файлів primary
workspace та review checkout збігаються.

**Повний gate пройшов за 9 хв 24 с** (227 завдань: 29 виконано,
198 up-to-date). **Debug 2704, Release 2310, Play 2310** — нуль
failures/errors/skips. Незмінні Core 133 результати використано повторно.
Чотири MediaSession controller тести та новий захист від старого END
також пройшли в повній debug-регресії.

Усі **49 screenshot goldens незмінні**, нуль added/changed. Три записані
background previews в ignored build не є новими еталонами. Компіляція
debug/test APK і обох app/harness benchmark варіантів, Detekt і lint
Debug/Release/Play успішні. Дванадцять архітектурних/приватних/Premium/UI
repository checks пройшли. Rules, baseline, goldens, бюджети та залежності
не змінені. Компілятор без Kotlin warnings; lint має нуль errors/warnings
і по два наявні Hints щодо target API 36 та Compose BOM. Компіляція
benchmark варіантів не є новим вимірюванням performance.

**Mi A2: 23/23 native тестів за 99,480 с**, нуль failure/skip статусів:
чотири LocalVideo, п'ятнадцять lifecycle та чотири video-fit. Обидва нові
негативні кейси стали успішними без послаблення їхніх очікувань. Перевірено
replay після справжнього EOF через UI та MediaSession, збереження стану
ENDED без фонового restart, seek/release і HTTP HEAD/range/close.

Використано ту саму production-збірку, що пройшла focused gate; наступне
доповнення — тільки host-захисний тест. Обидва native-протестовані APK
збігаються за SHA-256 з фінальними APK повного gate. Інстальовано лише debug/test APK.
Дані основної release-версії не змінені. Три початкові private file hashes
debug-пакета збігаються після відновлення. Native-журнал:
`app/build/device-audit/instrumented-1168da957ccb4fbcbddb398a37c59450.txt`.

## Межі висновку

Це стабілізація повторного відтворення локальних файлів, не тест кожного
рядка репозиторію. Тести реальних Cast/DLNA-приймачів і матриця всіх
контейнерів/кодеків залишаються окремою роботою. Раніше виміряні перевищення
frame-time бюджетів не оголошені виправленими. Жодної зміни версії,
публікації GitHub чи вимкнення комп'ютера цей прохід не виконує.

Ignored evidence: `build/local-video-completion-*.log` та
`app/build/device-audit/`. Приватні recovery-архіви не публікуються.
