# Плеєр і продуктивність: виправлення аудиту 5 вересня

Перевірка: 6 вересня 2026. Обсяг — сім знахідок з
[аудиту плеєра](PLAYER_UI_PERFORMANCE_AUDIT_2026-09-05.md) та пов’язана синхронізація налаштувань.
Це не повторна сертифікація всього застосунку для Google Play, усіх телефонів і телевізорів.

Наступний прохід: [M3U parser — CPU, cancellation та повторні Mi A2 виміри](PLAYLIST_PARSER_FIX_VERIFICATION_2026-09-06.md).

## Зміни

| Знахідка | Реалізація | Регресійна перевірка |
| --- | --- | --- |
| U-01: Pause не скасовував intent під час buffering | Єдина `PlayerViewModel.togglePlayback()` читає Media3 `playWhenReady`. Inline, fullscreen, double-tap і mini використовують її; UI окремо відображає intent. Локальний toggle заблокований для Cast/DLNA й закритої сесії. | Android: підготовка, пауза, 30 toggle, release, обидва remote-прапорці; Compose: Pause під час buffering. |
| U-02: контролі зникали під час взаємодії | Повторний touch дає новий інтервал; натиснутий pointer, keyboard focus, відкритий sheet або touch exploration утримують контролі. Враховано рекомендований Android timeout, щонайменше 3 секунди. | Compose clock: новий інтервал, відкриття/закриття sheet, pointer/focus; Robolectric AccessibilityManager: touch exploration і подовжений timeout. |
| U-03: недоступні Previous/Next виглядали активними | Availability походить із session state machine; disabled semantics і вигляд у fullscreen, inline та error card. Один канал не є власним наступним/попереднім. | Порожня/закрита сесія, один канал, початок/кінець, wrap on/off; Compose disabled assertions. |
| O-01: prefetch selection блокував Main | Відбір на наявному playlist CPU worker; порожні favorites/last-watched не запускають повний scan; ліниві категорії, ранній ліміт, збережені priority/dedup/cache semantics, cancellation probes. | Тест production dispatcher; 100k список, який падає при зайвому доступі; cancellation; повтор Mi A2. |
| O-02: prefetch поновлювався під час rebuffer | PlaybackActivity означає потребу пріоритету мережі: intent + активна сесія, а не лише `isPlaying`. Remote також утримує gate. Pause/close/ended/fatal звільняють local gate. | Чиста policy, Android intent/gate, interruption/cache-clear тести IconController. |
| O-03: пошук сканував на кожну клавішу без cancellation | Debounce 180 ms; query trim один раз; CPU-пошук із cancellation на кожному елементі. Новий query одразу прибирає старий результат. Індекси й позиція початкового scroll обчислюються поза Main у межах активної сесії. | Unicode/case/пробіли, scoped indices, no-match/empty, скасування 100k; Android вибір через full channel picker. |
| O-04: Midnight непотрібно декодував artwork tone | Вимкнений tone повертає null до запуску Compose producer/resolver. | Compose: resolver не викликається. |

Додатково: вже відкритий плеєр тепер підхоплює зміни wrap-around та auto-skip з налаштувань;
кнопки й next-channel preview оновлюються без повторного prepare або скидання recovery budget.
Підписка належить ViewModel; `onCleared()` відписує її, queued callback після закриття ігнорується.
Це покривається preference observer unit-тестами та Android-тестом на існуючому плеєрі.

## Візуальна частина

- Play/Pause збережено 66 dp; secondary controls і мінімальні touch targets не зменшено.
- Замість gear для More — трикрапка в обох layouts.
- Постійний ряд яскравості/гучності прибрано з відео. Його кнопки доступні через More → Brightness and volume у scrollable sheet в portrait і fullscreen; жест лишився додатковим способом.
- Довгі назви можна читати у повному списку каналів; великі шрифти не стискаються для вміщення preview.
- Golden `player_controls_landscape.png` оновлено навмисно та переглянуто. Знімки Mi A2 при 100% / 200% і timer 60:00 переглянуто; у цих fixtures основні кнопки не перекриваються.

## Вимір Mi A2

Та сама production selection fixture: 40 000 каналів без tvg-id, порожні favorites/last-watched,
40 каналів першої групи, limit 300. Три виміри, debug ART, інструментальний worker, не Main.

| Метрика | До | Після |
| --- | --- | --- |
| Prefetch selection | 471 / 376 / 389 ms | 1 / 1 / 1 ms |
| Вибрано каналів | 40 | 40 |

Другий повний Android-прогін також дав 1 / 1 / 1 ms; його raw-файл збережено окремо
в `shots/player-controls-fixes-2026-09-06/repeat-28/playlist-device-measurement.txt`.

Точність таймера — мілісекунди. Це не вимір швидкості завантаження з мережі чи startup.
Dispatcher-тест окремо підтверджує, що production `IconController` виконує selection на
`ua-cast-playlist-cpu`, а не на потоці виклику.

Парсинг не змінювався. У цьому повторі parse+group для 40k зайняв 3349 / 3308 / 3317 ms;
раніше було 2358 / 2360 / 2346 ms. Умови debug-прогонів не контролювали thermal/frequency state,
тому не робимо висновку про причинну регресію або прискорення parser. Алокації залишилися близько
78,6 MB на прохід — це cumulative process allocations, **не peak або retained heap**.
Для надійного parser/memory budget потрібен окремий контрольований профільний прогін.
У другому повторі parse+group 40k: 3473 / 3434 / 3435 ms; обмеження інтерпретації ті самі.

Дані та знімки: `shots/player-controls-fixes-2026-09-06/`.
Перший Android-прогін: `app/build/device-audit/instrumented-78de2b3e0edb42928fb06153e2c37ae6.txt` —
27 успішних сценаріїв. Дані debug-пакета відновлено; системний font_scale залишився 1.0.
Приватні recovery `.tar` не є артефактами для публікації.

## Підсумкова перевірка

- **2080 локальних тестів:** 1966 app + 114 core, 0 failures/errors/skipped за актуальними XML-звітами.
  Core не змінювався в цьому проході; Gradle повторно використав його актуальний успішний результат.
- Повний фінальний gate — **BUILD SUCCESSFUL**, 1m 54s:
  `:core:test :core:detekt :app:verifyRoborazziDebug :app:detekt :app:lintPlay :app:bundlePlay
  :app:assembleDebug :app:assembleDebugAndroidTest --continue --offline --no-daemon --console=plain`.
- Успішні screenshot verification, app/core Detekt, lintPlay та debug APK/test APK/release AAB.
  Lint: **0 errors, 0 warnings, 11 hints**. Залишаються два повідомлення R8 про unsupported async
  parsing у class-file provider збиральника; залежності та правила не послаблювалися для їх приховування.
- Дев’ять guard-скриптів успішні: architecture boundaries, hardcoded colors, glow, locale format,
  document references, Cast icon placement, Detekt baseline, premium purity, sold-feature gating.
  Baseline: **0 suppressed findings**. `git diff --check` без whitespace errors.
- **28 сценаріїв Mi A2**: 13 player lifecycle, 4 video fit, 6 UI polish, 1 empty playlist,
  3 fullscreen diagnostic states, 1 playlist/prefetch measurement. Успішний повтор:
  `app/build/device-audit/instrumented-895bb49b4c564d61955771df10bc9721.txt`.
- Після останнього уточнення preview виконано окремий Android-повтор
  `existingPlayerObservesNavigationPreferenceChanges` на фінальному APK — **OK (1 test)**:
  `app/build/device-audit/instrumented-39fe230e2c2244ce94a3caee1c13507c.txt`.
  Він перевіряє live wrap/auto-skip, зникнення wrapped preview та disabled Next на останньому каналі.
- У кожному Android-прогоні runner підтвердив відновлення files/shared_prefs. Основний пакет
  `com.uacastplayer` не змінювався; тестувався `com.uacastplayer.debug`.

Проміжні падіння не пропускалися: оновлено тести перенесених controls, у dispatcher-тесті враховано
coroutine debug suffix, а в auto-hide тестах явно застосовано UI snapshot перед просуванням ручного
Compose clock. Додано окремі перевірки accessibility timeout та touch exploration.

**Вердикт щодо цього обсягу: сім підтверджених знахідок виправлено й перевірено.**
Немає підстав переносити цей висновок на неперевірені зовнішні інтеграції нижче.

## Post-change architecture check

- Media3 залишається у PlayerViewModel; стан навігації — у наявній session state machine.
  UI більше не реалізує власних engine toggle-умов.
- Політики network priority та channel search не створюють scope і не мають Android/UI runtime.
- CPU-відбір використовує наявний bounded executor та cancellation власника, без нового глобального pool.
- Auto-hide й level sheet володіють лише presentation/platform UI state. Accessibility listener
  відписується при disposal; preference listener — при завершенні ViewModel.
- Не додано нових broad suppressions або baseline entries. Публікація, зміна версії, dependencies,
  основного пакета на телефоні та старі незакомічені зміни не входили в цей прохід.

## Межі висновку

Залишаються окремими перевірками: живе тривале декодування відео та FPS/peak heap,
повний ручний TalkBack/touch-grid, контраст на різних реальних відеокадрах,
реальні Chromecast/Hisense VIDAA, Google Play billing та матриця Android/OEM.
Release Macrobenchmark, який перезаписує fixtures основного пакета особистого телефона,
не запускався. UI fixtures не підтверджують працездатність усіх сторонніх IPTV URL.
