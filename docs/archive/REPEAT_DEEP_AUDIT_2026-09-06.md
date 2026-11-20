# Повторний глибокий аудит UA Cast Player — 2026-09-06

## Висновок

**Масовий production-реліз зараз: NO.** Застосунок має добру основу, суттєве регресійне
покриття та реальні попередні виправлення. Проте повторний аудит виявив обхід HLS memory
budget, обхід playback arbitration системними медіакомандами й блокувальний SAF-запит
на Main. Зелені тести цих шляхів поки не покривають.

Підсумок нових висновків: **0 підтверджених P0; 3 P1; 3 P2**. Це не шість відтворених
на телефоні падінь: один дефект відтворено в ізольованій JVM, решта мають конкретні
execution paths у поточному коді, з явно зазначеними умовами. Не додаю generic Android
warnings, невиміряні «витоки» чи припущення про несумісність Hisense як підтверджені баги.

Код застосунку, тестів і конфігурацію цього проходу не редагував. Створено цей звіт та
діагностичні build outputs. APK на телефони не встановлював, дані пристроїв не змінював,
CI не запускав, нічого не публікував.

## Обсяг і достовірність

- Поточний робочий стан, включно з попередніми незакоміченими змінами; не лише Git HEAD.
- Інвентаризація 464 Kotlin production-файлів у `app/src/main` та `core/src/main`.
- Наскрізний розбір критичних шляхів: імпорт → збереження → UI; MediaSession → engine →
  ViewModel; local/Cast/DLNA handoff; proxy read → unwrap/flatten → rewrite → response;
  cancellation/cleanup; EPG/icons; manifest, privacy, updater, premium та CI.
- Пошукові перевірки по всіх production source roots не означають, що кожен рядок,
  кожну кнопку та кожну комбінацію пристрою перевірено під час виконання.
- Візуальна оцінка: поточний Compose-код, screenshot regressions і повторний перегляд
  збережених Samsung screenshots. Це не новий інтерактивний обхід телефона.
- Системне керування звірено з локальними sources **Media3 1.10.1**, реально використаної
  версії, а не лише з коментарями про очікувану поведінку.

## Нові дефекти та сильно обґрунтовані ризики

### R1 — P1 / High: HLS memory budget застосовується після небезпечного розбору

**Статус:** `OutOfMemoryError` відтворено на compiled production `PlaylistUnwrapPolicy`
у JVM з `-Xmx128m`; досяжність із proxy підтверджена кодом. Повний Android process crash
через HTTP у цьому проході не відтворювався.

**Файли / функції:**

- [ProxyServer.kt:453](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:453>) — `serveUpstreamPlaylist`.
- [PlaylistUnwrapPolicy.kt:26](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/proxy/PlaylistUnwrapPolicy.kt:26>) — `unwrapTarget`.
- [HlsMediaPlaylist.kt:67](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/proxy/HlsMediaPlaylist.kt:67>) — `HlsMediaPlaylistParser.parse`.
- [HlsFlattenedStream.kt:235](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/HlsFlattenedStream.kt:235>) — окремий `fetchPlaylist` також використовує цей parser.
- [ProxyManifestResources.kt:43](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyManifestResources.kt:43>) — захищений `rewrite`, до якого виконання ще не дійшло.

**Тригер:** upstream повертає синтетичний malformed HLS із великою кількістю коротких
рядків. Використаний текст: `#EXTM3U\n#EXT-X-TARGETDURATION:6\n` + 2 000 000 повторень
`a\n`. Це **4 000 032 ASCII bytes**, менше вхідного cap 4 MiB. Не стверджую, що це
коректний HLS за специфікацією: саме некоректний ввід має відхилятися без OOM.

**Шлях:** `readPlaylistText` пропускає byte budget → default `unwrapWrapperPlaylists=true`
→ `unwrapTarget` утримує повний `lines.toList()` → `parse` створює ще один список рядків
та `segmentUris` → тільки потім мав би виконатися bounded rewrite.

**Очікується:** рання контрольована відмова без великого розширення об'єктів у heap.

**Фактично:** OOM виникає до нового ліміту 100 000 рядків у `RewriteBudget`. Попередній
регресійний тест викликає rewrite напряму і тому не перевіряє цю обхідну гілку.
Ліміт bytes не обмежує кількість String/list objects.

**Доказ:** [repeat-audit-proxy128.log](<C:/UA CAST/build/repeat-audit-proxy128.log>) містить
`MAX_HEAP=134217728`, `UNWRAP_INPUT=4000032`, `CONFIRMED_UNWRAP_OOM=OutOfMemoryError`.
Прямий parser у окремому запуску з тим самим input пройшов; це підкреслює вплив
одночасно утримуваних колекцій. Окремий flatten map на 1 млн URI також пройшов —
йому не приписується невідтворений OOM.

**Наслідок:** аварійне завершення/втрата доступної пам'яті під час proxy casting на
малому heap; навантаження від кількох response workers може погіршити ситуацію.

**Виправлення:** спільний структурний budget до unwrap/parse/flatten/rewrite; bounded
однохідний розбір без паралельних повних списків; послідовне resolve URI у flatten.
Не лікувати перехопленням `OutOfMemoryError` чи збільшенням heap.

**Обов'язковий тест:** upstream HTTP → фактичний proxy із default flags, окремо flatten
та rewrite; hostile input у memory-constrained процесі; рання відмова, збереження
попередніх manifest leases, healthy наступний запит і контрольоване завершення.

### R2 — P1 / High: системний Play обходить правила власника відтворення

**Статус:** сильно підтверджений execution path у застосунку та sources Media3;
повний external MediaController/device сценарій ще не виконаний.

**Файли / функції:**

- [PlayerViewModel.kt:131](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:131>) передає raw `exoPlayer` у MediaSession.
- [PlayerMediaSession.kt:23](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerMediaSession.kt:23>) — `MediaSession.Builder(context, player)`.
- [PlayerMediaSession.kt:85](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerMediaSession.kt:85>) — callback перехоплює лише channel next/previous.
- [PlayerViewModel.kt:461](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:461>) — захищений UI `togglePlayback`.

**Тригер:** відкритий локальний MediaSession/controller → початок Cast або DLNA → Play
від цього controller, наприклад зовнішньої медіаповерхні. Для доказового тесту слід
тримати Activity foreground, щоб не змішувати сценарій із background audio-focus rules.

**Шлях:** remote connection → `exoPlayer.stop()` у VM (рядки 213/268), але media item
залишається та MediaSession живий → `COMMAND_PLAY_PAUSE` делегується SDK → Media3
`MediaSessionImpl` викликає `Util.handlePlayButtonAction(playerWrapper)` → в `STATE_IDLE`
виконує `prepare()`, потім `play()`.

**Очікується:** локальний engine не відновлюється, поки відтворення належить receiver;
системні та внутрішні команди проходять один arbitration policy.

**Фактично / ризик:** `togglePlayback` із guard `isRemoteCasting` не викликається.
Listener синхронізує UI, але не зупиняє engine при такому запуску. Можливі два
одночасні споживачі потоку й звук телефона під час TV playback. Провайдер з одним
дозволеним підключенням може перервати потік receiver.

Споріднена проблема: системний Play після sleep expiry не очищує VM `sleepTimerExpired`,
на відміну від UI Play; отже правила retry/foreground і фактичний engine intent можуть
розійтися. Це частина того самого bypass, не окремий дубль P1.

**Виправлення:** session-facing player/adapter із явним маршрутизуванням play/pause/
prepare/stop через власника playback і динамічними available commands. Для remote
або коректно делегувати команди receiver, або не пропонувати unsupported дії.
Обережно уникнути рекурсії adapter → VM → adapter.

**Обов'язковий тест:** справжній MediaController, а не виклик dispatcher helper;
local → Cast/DLNA → controller.play; expiry → controller.play; fatal error → Play;
late command після player close. Перевіряти engine requests, owner та UI разом.

### R3 — P1 / High: запит назви SAF-файла блокує Main

**Статус:** статично підтверджене блокувальне виконання; ANR залежить від поведінки
DocumentsProvider, окреме ANR на телефоні не відтворювалося.

**Файли / функції:**

- [MainAppContent.kt:53](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/MainAppContent.kt:53>) — `OpenDocument` result callback.
- [AppPlaylistActions.kt:14](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/AppPlaylistActions.kt:14>) — пряме делегування.
- [PlaylistController.kt:214](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/PlaylistController.kt:214>) — `loadPlaylistFromFile`.
- [PlaylistFileLoader.kt:59](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/playlist/PlaylistFileLoader.kt:59>) — `documentName`.

**Тригер:** користувач вибирає файл у повільному/завислому cloud або OEM provider.

**Шлях:** callback на Main → `loadPlaylistFromFile` → `documentName` → синхронний
`contentResolver.query(...)`. Запит і читання cursor відбуваються до `launchLoad`
та навіть до публікації `isLoading=true`.

**Очікується:** responsive UI та скасування/заміна поточного імпорту незалежно від
повільного metadata provider. Назва файла — optional metadata.

**Фактично / ризик:** зависає UI; достатньо довга Binder-відповідь здатна спричинити ANR.
`runCatchingNonFatal` обробляє exception, але не переносить виконання з Main.
Сам M3U parse уже правильно винесено; не оголошую весь pipeline main-thread роботою.

**Виправлення:** suspend metadata lookup на IO в межах актуальної load generation,
fallback display name, cancellation signal де provider його підтримує. Coroutine timeout
сам по собі не гарантує переривання блокувального Binder call.

**Обов'язковий тест:** fake/тестовий ContentProvider затримує query; Main залишається
responsive, новий вибір скасовує логічний старий результат, late name не перезаписує новий.

### R4 — P2 / Medium: помилка збереження sources не доходить до звичайного UI

**Статус:** підтверджений шлях втрати результату запису; disk-fault сценарій нового
додавання не запускався в цьому проході. Це не повернення виправленого migration bug.

**Файли / функції:**

- [PlaylistController.kt:165](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/PlaylistController.kt:165>) — `setPlaylistDisplayName`.
- [PlaylistController.kt:338](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/PlaylistController.kt:338>) — `persistSources` / `awaitSourcesPersistence`.
- [PlaylistSourceStore.kt:38](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/playlist/PlaylistSourceStore.kt:38>) — `save(): Boolean`.
- [AddPlaylistScreen.kt:110](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/playlist/AddPlaylistScreen.kt:110>) — name commit, потім `onPlaylistLoaded()` без очікування durability.

**Тригер:** storage full, write/rename failure або відмова перезаписати newer source format.

**Очікується:** користувач знає, що імпорт доступний лише в поточній сесії або що
зберегти джерело не вдалося; доступна повторна спроба.

**Фактично:** sources/active state змінюються оптимістично, екран закривається. Результат
`false` зберігається у приватному `sourceSaveResult`; нормальне додавання його не читає.
`awaitSourcesPersistence` підключено до backup flow, але не до цього UI. Після restart
нове джерело може зникнути. Під час removal snapshot deletion також запущено незалежно
від успіху source-list commit, тому старий запис може повернутися вже без cache.

**Наслідок:** хибне підтвердження збереження/видалення і повторна ручна робота.
Не стверджую, що кожна snapshot cache failure безповоротно втрачає плейлист:
URL/SAF джерело часто дозволяє його відновити.

**Виправлення:** явний результат source mutation, observable saving/error state,
узгоджена політика rollback/retry; irreversible cleanup після durable commit.
Покрити disk-fault + restart для звичайного add/remove, не лише migration/backup.

### R5 — P2 / Medium: 11-й плейлист виглядає доданим, але не запам'ятовується

**Статус:** детермінований дефект логіки, підтверджений UI → controller → policy шляхом.

**Файли / функції:**

- [PlaylistSourcePolicy.kt:22](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/playlist/PlaylistSourcePolicy.kt:22>) — `MAX_SOURCES=10`.
- [PlaylistSourceSheet.kt:98](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/home/PlaylistSourceSheet.kt:98>) — Add доступний без capacity condition.
- [PlaylistController.kt:186](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/PlaylistController.kt:186>) — `LimitReached -> Unit`.
- [AddPlaylistScreen.kt:110](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/playlist/AddPlaylistScreen.kt:110>) — success dismiss незалежно від результату додавання source.

**Тригер:** 10 збережених distinct sources → додати 11-те distinct джерело, load успішний.
Оновлення вже наявного ID — інший сценарій і цього ліміту не досягає.

**Очікується:** зрозуміле повідомлення про ліміт і вибір, що видалити/замінити,
до витрат на завантаження; controller також не повинен мовчки ковтати відмову.

**Фактично:** channels завантажуються, pending source очищується, нове джерело не потрапляє
до списку, UI завершує flow як успішний. Після перемикання/перезапуску його потрібно додавати знову.

**Виправлення:** capacity state для UI + typed `LimitReached` outcome на command boundary;
не підвищувати ліміт як спосіб приховати silent failure. UI regression: 10 → 11,
duplicate existing source та cancellation.

### R6 — P2 / Medium: system next/previous не відповідає реальним channel commands

**Статус:** підтверджена невідповідність app code контракту Media3 1.10.1;
конкретні headset/watch UI ще потребують integration перевірки.

**Файли / функції:**

- [PlayerMediaSession.kt:58](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerMediaSession.kt:58>) — `onConnect` додає next/previous media item commands.
- [MediaSessionCommandPolicy.kt:18](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/MediaSessionCommandPolicy.kt:18>) — map лише двох `*_MEDIA_ITEM` команд.
- [PlayerViewModel.kt:421](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:421>) — engine містить один MediaItem.

**Тригер:** controller очікує channel next/previous через системну MediaSession.

**Очікується:** available commands відображають список каналів, а next/previous викликає
channel navigation з правильною boundary/wrap policy.

**Фактично:** додавання команди до `ConnectionResult` не додає її raw player's capabilities.
Media3 `ConnectedControllersManager.isPlayerCommandAvailable` перевіряє обидва набори;
`MediaSessionStub` відхиляє unavailable command ще до app callback. Media3 `Util.getAvailableCommands`
додає `SEEK_TO_NEXT_MEDIA_ITEM` лише за `hasNextMediaItem`, а попередню — за `hasPreviousMediaItem`.
У singleton timeline цих items немає. Альтернативні `SEEK_TO_NEXT/PREVIOUS` app policy
явно не обробляє (це навіть зафіксовано helper unit tests).

**Наслідок:** відсутні/непрацюючі канальні кнопки системних controls або звичайний seek
замість перемикання. Це не доведений crash.

**Виправлення:** у тому самому session adapter, що для R2, узгодити commands, timeline
semantics і channel navigation. End-to-end MediaController tests повинні перевіряти
спочатку `availableCommands`, потім дію, а не тільки policy mapping.

## Архітектурна оцінка

Структура не потребує переписування на «ідеальну Clean Architecture». MVVM не забороняє
ViewModel звертатись до repository; окремий use case для кожного getter не дасть вимірюваної користі.

| Зона | Фактичний стан | Наступна зміна з практичною користю |
|---|---|---|
| Модулі | `app → core`, окремий measurement `baselineprofile`; core справді Kotlin/JVM | Зберегти напрямок. Feature modules додавати за потребою build isolation, не заради кількості |
| Playback ownership | Одна VM володіє engine; recovery/channel policies уже винесені; зовнішні commands обходять VM | Session command boundary R2/R6; одна точка ownership/intent arbitration |
| PlayerViewModel | 837 фізичних рядків; engine events, lifecycle, remote state, timer, tracks, navigation | Виносити ownership coordinator з тестами поведінки, не просто розкладати extension methods |
| Cast/DLNA | Cast port існує; Player VM прямо отримує singleton DLNA repository | Аналогічний DLNA port/injection дасть реальні deterministic handoff/command tests без singleton setup |
| CastSessionRepository | 814 рядків; lifecycle, status, routes, watchdog integration; окремі recovery owners уже є | Змінювати після command arbitration; не створювати ще один незалежний StateFlow «істини» |
| AppViewModel | 737 рядків, composition root + багато feature bridges | Відокремити створення dependencies від UI facade; тестувати playlist/settings/backup mutations незалежно |
| Proxy | Routing/serving/registry частково розділені, але input budget не охоплює pipeline | Спільний HLS contract R1; після цього виміряти monitor hold time в manifest rewrite |
| Persistence | Atomic helpers і generation guards є; результат durability не всюди доходить до UI | Явні source mutation results R4/R5, durable commit перед cleanup |

Фізичні рядки включають comments/blank lines, це не метрика Detekt complexity.
`AppIcons.kt` (~1241 рядок векторних описів) не є важливішим за невеликий небезпечний parser.
Порожній baseline і зелений dependency guard не доводять правильність state ownership:
R2 проходить ці gates і залишається поведінковою проблемою.

## UI/UX: що покращувати далі

Оцінка за кодом, golden tests та збереженими Samsung screenshots, зокрема font scale 2.0:
дизайн цілісний, controls стали зрозумілішими. Основний резерв — інформаційна ієрархія
та чесні стани, а не додаткові прикраси.

1. **Спочатку R4/R5.** «Додано» та «Збережено» не повинні означати різне без пояснення.
   Вказувати ліміт, saving failure, повторну спробу, тимчасово завантажене джерело.
2. **Fatal player state компактніше.** На `player-error-actions.png` чорний video rectangle,
   великий error block і повтор назви каналу займають більшу частину viewport. Розглянути
   error overlay у video area або згортання порожньої області; Retry/Next/Back залишити
   явними. Не зменшувати системний font scale заради вміщення.
3. **Менша вага background progress.** На `settings-playback.png` EPG/banner займає значну
   верхню область під час пошуку. Після первинного імпорту доречний компактний status з
   деталями по натисканню. Успішне фонове оновлення не має конкурувати з поточною дією.
4. **Єдиний зрозумілий target.** Узгодити local/TV/connecting/recovering/failed стан у full
   player, mini player та system media controls. Remote локальні track/quality дії вже
   приховані; не повертати непрацюючі кнопки. R2 показує, чому системна поверхня теж важлива.
5. **Accessibility перевірити наживо.** TalkBack focus order, назви toggle/slider states,
   200% text із довгими українськими/іспанськими назвами, keyboard/D-pad navigation.
   Auto-hide вже враховує touch exploration, focus та recommended timeout — це зберегти.
6. **Теми:** AZURE/CINEMA/MIDNIGHT вже є. Не додавав би четверту схожу темну тему першою.
   Light/high-contrast mode — optional product improvement після реального daylight/
   contrast testing, а не виправлення підтвердженого crash.
7. **Анімації:** не розширювати до вимірювання frame times на слабкому телефоні.
   Entry animation уже обмежена; перевірити reduced-motion та battery-saver journeys.
   Ціль — збереження focus/scroll і відсутність dropped frames, не кількість transitions.

Візуально переглянуті файли:
[player-error-actions.png](<C:/UA CAST/shots/deep-audit-fixes-2026-09-06/samsung/ui-polish/player-error-actions.png>),
[settings-playback.png](<C:/UA CAST/shots/deep-audit-fixes-2026-09-06/samsung/ui-polish/settings-playback.png>).
Чорне відео тут належить synthetic error fixture; не є доказом відмови справжнього каналу.

## Продуктивність, безпека та релізні прогалини

### Продуктивність

- **Перший пріоритет — R1, потім R3.** Зниження allocations до небезпечного парсера та
  усунення blocking metadata lookup важливіші за cosmetic recomposition changes.
- Fixed worker icon prefetch, dedup, low-end/playback gates, parsed EPG cache, heap-based
  programme retention і cancellation уже реалізовані. Не пропоную їх «додати» вдруге.
- Cold/warm startup, 40k-channel journeys та 350k XMLTV parse Macrobenchmarks уже написані.
  Вони потребують актуальних release-like device results, p50/p95, peak memory, GC pauses,
  frame timing та повторюваного thermal/network setup. Наявність harness ≠ виконаний benchmark.
- У CI виявлено compile/isolation gate для benchmark, а не регулярний device performance
  budget gate. Host timing ceilings не замінюють такий gate.
- Baseline profile існує, має заголовок генерації 2026-07-30; після значних змін варто
  регенерувати й перевірити ефект Partial compilation проти baseline. Сам вік не є багом.
- 100k channels слід тестувати також на decode/cache/restore/search, не лише M3U parsing.
  Попередні sample allocations на Samsung не є повним peak heap застосунку.

### Безпека та конфіденційність

- Proxy token, рання авторизація перед response pool, bounded queues/per-IP cap і absolute
  admission deadline існують. Bind на `0.0.0.0` необхідний LAN receiver; сам по собі не CVE.
  R1 залишається ресурсним DoS через вміст підключеного upstream.
- Cleartext HTTP дозволено для IPTV. UI вже попереджає про HTTP credentials; blanket ban
  зламає частину джерел. Не називаю підтримку HTTP прихованою витоковою вразливістю.
- `allowBackup=false`, private app files, обмежений diagnostics FileProvider, redaction
  логів, signature gate sideload installer і Play-specific removal install permissions є.
- Privacy WebView існує, але offline/static: JS, DOM storage, file/content access,
  network loads та legacy file-origin access вимкнено; `destroy()` виконується onDispose.
  Підстав оголошувати WebView exploit за самою наявністю WebView не знайдено.
- Playlist source file та explicit backup не мають додаткового app-level encryption;
  backup UI вже чесно попереджає про credentials. Password-encrypted export і Keystore
  storage — додатковий захист за окремою threat model, не доказ доступності приватних
  файлів будь-якому Android застосунку.
- **Не виконано:** актуальний vulnerability-advisory/SCA scan усіх transitive dependencies,
  зовнішній pentest LAN, перевірка Play Console Data safety/декларацій. Висновок «усе
  безпечно та конфіденційно на 100%» не обґрунтований.

### Release/CI

- Unit/screenshot, quality, APK/AAB та API 24/30/36 instrumentation jobs уже розділені.
  Mapping, доступні native symbols, dependency text report, APK analyzer та size diff
  уже передбачено workflow. Dependency text report — не machine-readable SBOM/SCA.
- `STORE_IS_LIVE=false`: premium commerce наразі вимкнений. Це свідома конфігурація,
  не підтверджений дефект безкоштовного релізу. Увімкнення потребує real Play purchase,
  restore, refund/revoke, pending, offline і account-change перевірок.
- SDK compile/target = 36, min = 24; version name = 0.9.3. Немає підстав підвищувати
  номер автоматично через аудит. Публікувати слід конкретний перевірений commit/artifact.
- Реальні Hisense VIDAA, Chromecast reconnect/receiver reject та фінальний повтор
  device suite залишаються evidence gaps, не автоматично підтвердженими дефектами.

## Повторні перевірки цього проходу

**Примусовий повтор без cached task skip:**

```powershell
.\gradlew.bat :core:test :app:verifyRoborazziDebug :app:processReleaseMainManifest :app:processBenchmarkReleaseMainManifest :app:processNonMinifiedReleaseMainManifest --rerun-tasks --offline --no-daemon --console=plain
```

- **BUILD SUCCESSFUL in 2m; 46 actionable tasks, 46 executed.**
- **2016 app + 114 core = 2130 tests; 346 suites; failures/errors/skipped = 0.**
- Roborazzi verification пройшла; golden images не перезаписувалися.
- [Повний журнал](<C:/UA CAST/build/repeat-deep-audit-tests.log>).
- R1 окремо відтворено в JShell, цей hostile scenario не входить у 2130 зелених tests.

Повторно пройшли **14 guard scripts**: architecture boundaries, hardcoded colors,
glow/text, locale formatting, doc references, cast icon placement, empty Detekt baseline,
premium purity, sold-feature gating, AppLog sensitive variables, onCleared super,
legal assets, merged Play distribution та benchmark fixture isolation.
Остання перевірка спочатку не мала generated release manifest; після generation вище
її повторено успішно. Це відсутній локальний prerequisite, не source isolation failure.

Detekt analysis/lintPlay/Play AAB із попереднього проходу були зеленими; у цьому проході
повторено baseline guard, але не самі Detekt/lint/AAB tasks. Попередні 0 lint errors /
0 warnings та два R8 build-tool messages не видаються за новий запуск.

Device evidence з попереднього проходу: SM-M156B API 36, 35 сценаріїв, 34 pass,
один failed assertion у sleep fixture; fixture уточнено, але фінальні 36 device cases
не перевиконані через від'єднання пристрою. Цей факт не замінюється зеленими host tests.
Докладно: [попередня перевірка виправлень](<C:/UA CAST/docs/DEEP_AUDIT_FIX_VERIFICATION_2026-09-06.md>).

## Пріоритетна матриця відсутніх перевірок

| Поведінка | Що покривається зараз | Чого бракує | Пріоритет |
|---|---|---|---|
| Hostile HLS | Parser/rewrite helpers, manifest leases, loopback resource fetch | Повний upstream → unwrap/flatten path у 128 MiB, concurrent requests | P1 |
| MediaSession | Mapping/dispatcher unit tests | Реальний controller → available commands → engine/VM/remote ownership | P1 |
| SAF import | Parsing/CPU dispatcher/storage regressions | Slow provider metadata + responsive Main + cancellation | P1 |
| Source persistence | Atomic storage, migration, backup result handling | Normal add/remove failure → UI + restart, cleanup ordering | P2 |
| Source capacity | Policy та звичайний playlist UI | 10 → 11 source end-to-end, без хибного success dismiss | P2 |
| Lifecycle | Багато host/Android lifecycle tests | Фінальні 36 cases на доступному authorized phone; media buttons | P1 release evidence |
| Cast/DLNA | Reducers, generations, watchdog/proxy tests | Реальні receivers: Wi-Fi loss, expired URL, швидкі switches, firmware profiles | P1 release evidence |
| Premium | Fake provider/entitlement/query regressions | Play internal testing, purchase/restore/refund/account change | Перед monetization |
| Performance | Host ceilings і device benchmark harness | Release frame/heap/GC budgets на слабкому та середньому пристрої | P2, до широкого rollout |
| Security/packaging | Source guards, manifest/installer/privacy tests | Dependency SCA/SBOM, final signed artifact та store declarations | До публікації |

## Top 10 ризиків і рекомендований порядок

Це список ризиків, **не десять заявлених багів**: останні пункти є прогалинами доказів
або архітектурними напрямками.

1. R1: HLS input → heap amplification до validation.
2. R2: MediaSession → engine поза ownership policy.
3. R3: SAF metadata → blocking Main.
4. R4: source mutation → відсутність UI durability outcome.
5. R5: capacity limit → хибне успішне додавання.
6. R6: system next/previous → неправильний capability contract.
7. Реальні receiver/network/firmware сценарії залишаються не підтвердженими.
8. Фінальний device regression suite ще не повністю зелений після fixture correction.
9. Немає актуального регулярного release device performance budget; SCA/Play evidence неповне.
10. Кілька точок прийняття playback рішень у VM/SDK/DLNA ускладнюють подальші зміни.

**Порядок робіт:**

1. Єдиний HLS budget на вході всіх гілок; regression, що падає на поточному R1.
2. MediaSession adapter + справжні controller integration tests: закрити R2 і R6 разом.
3. Перенести SAF metadata з Main; slow-provider/cancel test.
4. Typed source mutation outcomes, UI capacity/durability, add/remove restart tests.
5. Повторити full host/quality/packaging gate та фінальний phone suite.
6. Перевірити реальні Cast/DLNA receivers; release-like low-end benchmarks; SCA/artifact
   checks. Не оголошувати сумісність Hisense доведеною без його участі.
7. UI information hierarchy, TalkBack/daylight testing; опційно light/high-contrast theme.
8. Лише після поведінкових контрактів — подальше розділення VM/repositories, якщо воно
   зменшує кількість ownership paths або полегшує незалежне тестування.

**Файли першими:** `PlaylistUnwrapPolicy.kt`, `HlsMediaPlaylist.kt`, відповідні proxy entry
points; `PlayerMediaSession.kt` разом із ownership частиною `PlayerViewModel.kt`;
`PlaylistFileLoader.kt` / `PlaylistController.kt`; далі `AddPlaylistScreen.kt` /
`PlaylistSourceSheet.kt`. Не починав би з `AppIcons.kt` або масового module split.

## Що вже зроблено правильно

- Справжня JVM core boundary та автоматичний контроль напрямку залежностей.
- Винесені recovery/channel policies з deterministic tests, generation guards для
  async results, cleanup listeners/jobs і bounded retry. GlobalScope у production scan не знайдено.
- Одна Activity-owned player/session пара; її lifecycle cleanup є. Це не гарантує
  ownership правил усіх зовнішніх commands — саме тому R2 виділено окремо.
- Atomic migration rollback, alias/metadata budgets, protected HLS leases, admission
  deadline, remote playback leases і Billing Unknown/snapshot distinction реально є в коді.
- Обмежений icon prefetch, EPG retention/cache, адаптація controls до remote та accessibility.
- Сильний host regression/screenshot gate, empty Detekt baseline і розділений CI.
- Усвідомлені privacy defaults і чесне приховування непідключеного premium store.

## Загальна оцінка стану

**Зрілий кандидат для контрольованого тестування, але не фінальна production-збірка.**
Архітектурний фундамент та UI вже достатні, щоб не переписувати застосунок. Найбільша
проблема зараз — прогалини на стиках добре протестованих компонентів, а не брак класів,
тем чи іконок. R1 доводить це особливо наочно: попередній memory fix працює всередині
rewriter, але небезпечний pipeline виконує інший parser раніше.

**Умови зміни вердикту на YES:** усунути R1–R3, перевірити реальні session commands,
прибрати хибні source success states, отримати зелений фінальний device gate та
релізні докази для заявлених Cast/Play можливостей. Неможливо чесно гарантувати
«всі баги виправлено» лише числом зелених unit tests.
