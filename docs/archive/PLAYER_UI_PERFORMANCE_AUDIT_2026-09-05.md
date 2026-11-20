# Плеєр: ергономіка та продуктивність — 5 вересня 2026

Наступний прохід: [реалізація та перевірка виправлень](PLAYER_UI_PERFORMANCE_FIX_VERIFICATION_2026-09-06.md).
Нижче збережено початкові знахідки й виміри до виправлення.

## Висновок

Основна ієрархія плеєра вдала: великий Play/Pause, сусідні канали поряд, другорядні дії окремо. Повне перемальовування не потрібне. Зараз найбільша користь буде від виправлення поведінки Play/Pause, автоприховування контролів та підготовки icon prefetch на головному потоці.

Це окремий аудит після [UI/UX-виправлень](UI_UX_FIX_VERIFICATION_2026-09-05.md), не твердження про безпомилковість усього застосунку. Код застосунку в цьому проході не змінювався; додано два діагностичні Android test-файли та цей звіт. Попередні незакомічені зміни збережено.

## Метод і межі

- Перевірено inline/fullscreen/mini UI, спільні кнопки, автоприховування, навігацію, Media3 callbacks, icon prefetch, пошук каналів і playlist CPU pipeline.
- Mi A2, Android 11/API 30, density 2,75: реальні production Compose-контроли fullscreen, українські підписи, шрифт 100% і 200%, активний таймер у другому стані. Синтетичні назви каналів, чорний фон **без відеодекодера**. Це не тест контрасту на всіх можливих відеокадрах і не вимір FPS відтворення.
- Розмір шрифту змінювався тільки через test CompositionLocal; системний `font_scale` залишився 1.0. Дані debug-застосунку резервувалися і відновлювалися `run-preserved-device-tests.ps1`.
- **215 локальних тестів**: player, player UI, performance, playlist parse budget; 0 failures/errors/skipped. Debug APK/test APK зібрані, detekt успішний.
- **4 діагностичні сценарії на телефоні**: 3 UI-стани й вимір parser/grouping/prefetch selection. Успіх діагностичного тесту означає отримання даних, а не автоматичне проходження всіх критеріїв зручності.
- Профільний release Macrobenchmark не запускався: поточний driver працює з основним пакетом `com.uacastplayer` і записує synthetic fixtures. Безпечний runner цього проходу захищає `.debug`, не основний пакет. Дані особистого телефона не стали benchmark-даними.

## Розміри й розташування

У повному landscape viewport Mi A2 (2160×1080 px, приблизно 785×393 dp) нижні та верхні основні кнопки вміщуються, зокрема при 200% і таймері 60:00. Перекриття основних кнопок між собою в цих станах не виявлено.

| Елемент | Виміряний видимий розмір | Висновок |
| --- | --- | --- |
| Play/Pause | приблизно 66×66 dp | Виразний основний контроль; залишити |
| Попередній / наступний | приблизно 46×46 dp | Розмір узгоджений із другорядною роллю |
| Back, телевізор, меню, PiP, fullscreen | приблизно 38×38 dp | Це розмір видимого кола, не повна touch area |
| Чипи наступних каналів | висота 30 dp при 100%, 44 dp при 200% | Візуально дрібніші за основні кнопки; довгі назви скорочуються |
| Таймер із 60:00 при 200% | приблизно 106×38 dp | На Mi A2 вміщується без витіснення PiP/fullscreen |

У `SmallRoundIconButton` і `RoundIconButton` є `minimumInteractiveComponentSize()`. Тому **38 dp видимого кола не оголошується автоматично порушенням мінімальної області натискання**. Поточна діагностика вимірює bounds семантичного вузла, не всі межі розширеного hit testing. Повний touch-grid та TalkBack-прохід ще потрібні.

Дизайнерські рекомендації, не підтверджені баги:

1. Зберегти великі Play/Pause та Previous/Next. Не збільшувати однаково всі іконки — це погіршить ієрархію й ущільнить layout.
2. Fullscreen одночасно показує 16 клікабельних вузлів у fixture з трьома наступними каналами. Яскравість/гучність можна зробити розгортаними, залишивши явний доступний шлях для TalkBack; не залишати ці функції лише жестам.
3. Для «Ще» краще три крапки, ніж шестерня: відкривається меню дій плеєра, а не налаштування застосунку. Це семантичне уточнення, не причина затримувати реліз.
4. Для довгих назв у наступних каналах — доступ до повної назви/списку. Не намагатися втиснути всі назви зменшенням accessibility-шрифту.

## Підтверджені проблеми поведінки

### U-01 — P1: Play/Pause не дозволяє скасувати намір відтворення під час buffering

**Доказ із коду:** [inline handler](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerInlineContent.kt:229>), [fullscreen handler](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerFullscreenContent.kt:167>), double-tap у тому самому файлі:58 та [mini handler](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/MiniPlayerBar.kt:151>) використовують `if (player.isPlaying) pause() else play()`.

Шлях: у плеєра `playWhenReady=true` → Media3 переходить у BUFFERING → `isPlaying=false` → кнопка знову викликає `play()` і залишає намір true → після приходу даних починається звук/відео, хоча користувач намагався зупинити очікування. Повторні натискання не перемикають намір. `PlayerUiState` не зберігає окреме wantsToPlay, а `onPlayWhenReadyChanged` лише скасовує recovery.

Це статично доведена логіка, не новий запис реального stall на телефоні. **Виправлення:** єдина команда toggle у власника player state, окремий намір `playWhenReady`, узгоджені підпис/іконка; регресія BUFFERING → pause → READY не повинна запускати відтворення. Уже правильний підхід у `BackgroundPlaybackPolicy` не виправляє ці окремі UI-handlers.

### U-02 — P2: controls можуть зникнути під час їх використання

[PlayerScreenEffects](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerScreenState.kt:121>) запускає фіксовані 3 секунди за ключами `controlsVisible` та `isPlaying`. Натискання +/- яскравості/гучності, accessibility focus або відкриття меню не перезапускають цей таймер.

Шлях: показати controls на живому потоці → через 2,8 с почати регулювання → через 0,2 с controls ховаються. **Виправлення:** єдиний сигнал взаємодії для reset; врахувати відкриті sheets та accessibility timeout; для touch exploration не ховати сфокусовану дію. Тестувати саме реальну триваючу playback-активність, а не paused fixture.

### U-03 — P2: кнопки переходу залишаються активними на кінці списку

[ChannelNavigationRow](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerInlineContent.kt:269>) та fullscreen overlay завжди передають активні callbacks. [PlayerNavigationController](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerNavigationController.kt:44>) нічого не робить, коли `nextIndex/previousIndex` повертає null.

Тригер: вимкнути циклічне перемикання, перейти до останнього/першого каналу або відкрити єдиний канал. UI обіцяє дію, але натискання не дає результату. **Виправлення:** окремі `canGoNext/canGoPrevious` зі state machine, disabled-семантика/вигляд; не плутати попередній елемент списку з історією останнього перегляду. Crash тут не виявлено.

## Продуктивність: фактичні вимірювання

### Mi A2 — debug ART, синтетичний M3U

Один невеликий warmup, три зразки на розмір; тестовий worker, без мережі, диска snapshot, EPG і Media3. Вимір включає production parse + grouping. Це не час від натискання «Імпортувати» до готового екрана.

| Каналів | Розмір UTF-8 | Parse + group, 3 зразки | Медіана | Виділені байти процесу за зразок |
| --- | --- | --- | --- | --- |
| 3 000 | 400 168 B | 243 / 180 / 178 ms | 180 ms | приблизно 5,8 MB |
| 10 000 | 1 351 168 B | 618 / 610 / 597 ms | 610 ms | приблизно 19,5 MB |
| 40 000 | 5 551 168 B | 2358 / 2360 / 2346 ms | 2358 ms | приблизно 78,6 MB |

Схоже на лінійне масштабування в цій fixture, не доказ відсутності всіх O(n²) шляхів. Різниця `art.gc.bytes-allocated` — сумарні алокації процесу за інтервал, **не retained heap і не peak heap**. `art.gc.gc-time` для 40k зростав на 421/534/440 ms; це GC time, **не вимір stop-the-world pauses**. Порівнювати ці числа з heap limit як прямий доказ OOM не можна.

Desktop JVM: 100k parse 156,8 ms; grouping 10,0 ms; пошук без збігів 11,2 ms. Ці цифри не характеризують швидкість телефона. Вхід desktop fixture близько 18 MB перевищує production URL/file limit 8 MiB, тому це тест алгоритму, не підтримуваного імпорту такого файла.

### O-01 — P1: підготовка prefetch блокує Main на великому плейлисті

Шлях: [AppViewModel](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/AppViewModel.kt:290>) передає `viewModelScope` → [IconController.startPrefetchJob](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/IconController.kt:203>) робить `scope.launch` без dispatcher → `runPrefetch` → [selectPrefetchChannels](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/IconController.kt:382>) → [PrefetchSelectionPolicy.select](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/icons/PrefetchSelectionPolicy.kt:39>).

`channels.filter { FavoriteKey.of(it) in priority.favoriteKeys }` сканує всі канали навіть за порожнього favorite set. Без `tvg-id` [FavoriteKey.of](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/favorites/FavoriteKey.kt:15>) обчислює SHA-256 stream URL для кожного елемента. Ліміт 300 застосовується після побудови проміжних колекцій.

**Вимір Mi A2:** 40 000 каналів без tvg-id, немає favorites/last-watched, вибрано лише 40 каналів першої групи: **471 / 376 / 389 ms** на саму production-функцію. Тест безпечно виконував її поза Main; напрямок dispatcher у застосунку доведений кодом. Це сильний доказ ризику помітного UI stall, не запис конкретного ANR.

Тригер обмежений увімкненим cache-prefetch та не-LOW_END профілем, коли playback gate дозволяє prefetch. На LOW_END цей bulk pass пропускається.

**Виправлення:** не сканувати favorites, якщо set порожній; не шукати last-watched, якщо його немає; ранній bounded selection; винести CPU-частину з Main з cancellation checks. Зберегти порядок priorities, dedup і generation-guard. Приймання: dispatcher-тест та повтор цієї fixture до/після, без Main блокування.

### O-02 — P2: prefetch може поновлюватися саме під час rebuffering

[PlayerViewModel.onIsPlayingChanged](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:149>) публікує `PlaybackActivity.setActive(isPlaying || isRemoteCasting)`. За BUFFERING локального потоку isPlaying стає false; [IconController](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/IconController.kt:89>) на false викликає `startPrefetchJob`, якщо збережений запит існує.

За дозволеного cache-prefetch і не-LOW_END профілю це повертає до шести icon downloads під час відновлення відеопотоку, а також запускає O-01. Кількість додаткового трафіку/затримку відтворення цим тестом не виміряно. **Виправлення:** gate має захищати preparing/buffering з активним наміром playback, а не тільки фазу, коли кадри вже відтворюються. Явна pause/close повинна дозволяти поновлення.

### O-03 — P2: новий пошук у списку плеєра не використовує bounded/debounced шлях

[PlayerChannelSheet](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerChannelSheet.kt:48>) на кожен символ запускає повне `indices.filter`, перевіряє `isBlank/trim` усередині циклу й матеріалізує всі позиції збігів. Це **не Main scan** — він уже винесений на `withPlaylistCpu`; але цей варіант helper не має cancellation probes усередині CPU-блока. Скасований застарілий запит може завершувати весь scan на спільному однопотоковому playlist worker перед наступним.

Основний `ChannelSearch` нормалізує запит один раз і обмежує результати до 200. **Виправлення:** спільна пошукова політика з індексами поточної сесії, нормалізація один раз, debounce, кооперативне скасування; для порожнього запиту не створювати нову boxed-колекцію всіх індексів без потреби. Не підмінювати список сесії глобальним плейлистом: parental filtering має зберегтися. Відчутну затримку саме цього sheet на Mi A2 ще не виміряно, тому не підвищено до P1.

### O-04 — P3: у Midnight виконується непотрібний artwork sampling

[ChannelInfoCard](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerInfoPanel.kt:140>) спочатку викликає `rememberArtworkTone`, а вже потім відкидає результат через `.takeIf { wallpaperTexture }`. [Producer](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/components/ArtworkTone.kt:51>) усе одно може резолвити файл та декодувати thumbnail.

Це зайва робота, не доведений великий battery drain. **Виправлення:** перевірити palette flag до входу в composable producer. Додати тест, що resolver не викликається в темі без artwork wash. Не потрібен загальний redesign кеша заради цього одного випадку.

## Що вже зроблено правильно / відхилені перебільшення

- PlayerViewModel Activity-scoped; `VideoSurface.onRelease` від'єднує PlayerView, не створює новий ExoPlayer на кожну рекомпозицію.
- Player/DLNA state збирається через `collectAsStateWithLifecycle`; sleep timer передається як State до маленького leaf-контролу, а не примусово оновлює весь плеєр щосекунди.
- Media3 buffer має байтові межі 8/16/24 MiB, `setPrioritizeTimeOverSizeThresholds(false)`; є conservative heap defaults та EPG retention budget.
- Playlist parse/grouping уже винесені на окремий CPU worker і мають cancellation probes. Проблема O-01 — інший шлях, не повернення старого parser-on-Main дефекту.
- Prefetch уже має bounded pool, dedup, priorities і LOW_END gate. Знайдене стосується підготовки черги та playback gate, не «тисяч одночасних coroutine».
- Сам факт `rememberInfiniteTransition` у LIVE не доводить витік або значний CPU drain. Ранні CPU-спроби в Compose test clock відхилені як нерепрезентативні, не використані для вердикту.
- TextureView не рекомендую сліпо замінювати на SurfaceView: код пояснює попередню проблему перехоплення натискань під Compose overlay. Спершу потрібен release GPU/video benchmark і повтор gesture/tap regression.

## Порядок подальших змін

1. O-01: прибрати дорогий selection із Main, зберігши семантику priorities.
2. U-01 та O-02: явний playback intent для toggle і prefetch gate; тести buffering/pause/recovery.
3. U-02: interaction-aware auto-hide та accessibility timeout.
4. U-03: доступність переходів на межах списку.
5. O-03/O-04: скасування пошуку й уникнення невикористаного artwork sampling.
6. Після цього — точкові дизайнерські зміни, вимір release FPS/startup/peak heap/battery на виділеному пристрої.

Цей аудит не виміряв release cold/warm startup, пікову пам'ять із реальним відео + XMLTV одночасно, GC pauses, FPS чи енергоспоживання тривалого перегляду. Не підмінюємо їх успішними unit-тестами.

## Артефакти

- Device measurement log: `app/build/device-audit/instrumented-8a423c656cbb401788668fd636df1c2f.txt`.
- CPU/allocation measurement: `shots/player-controls-audit-2026-09-05-verified/playlist-device-measurement.txt`.
- Початкові знімки UI: `shots/player-controls-audit-2026-09-05/` (system bars присутні); геометрія після повторного immersive apply — `shots/player-controls-audit-2026-09-05-verified/*.txt`.
- Кадри `*-verified/*.png` захопили стартове вікно; вони відхилені, не є доказом білого екрана застосунку. Фінальні кадри синхронізуються через Compose root capture.
- Фінальний UI-прогін: `app/build/device-audit/instrumented-4e26c2607455493589c1d6b8f2449cb0.txt` — 3/3 повторних діагностик. Перевірені візуально кадри: [100%](<C:/UA CAST/shots/player-controls-audit-2026-09-05-final/fullscreen-100.png>), [200% із таймером](<C:/UA CAST/shots/player-controls-audit-2026-09-05-final/fullscreen-200-timer.png>). Вони показують production controls, не реальний відеопотік.
- Приватні recovery `.tar` містять дані користувача: не публікувати.
