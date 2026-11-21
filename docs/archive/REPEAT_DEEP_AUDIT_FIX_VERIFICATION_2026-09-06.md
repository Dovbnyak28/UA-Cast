# Виправлення повторного аудиту — 2026-09-06

Обсяг: підтверджені R1–R6 з [повторного аудиту](<C:/UA CAST/docs/REPEAT_DEEP_AUDIT_2026-09-06.md>),
їх регресійні тести та два конкретні покращення інформаційної ієрархії UI.
Історичний аудит не переписано; попередні зміни робочого дерева збережено.

## Виправлення

| ID | Що змінено | Чим перевірено |
|---|---|---|
| R1 / P1 | Спільний allocation-light HLS budget до unwrap, flatten і rewrite; parser більше не створює два повні списки рядків; абсолютні segment URI у flatten обчислюються ліниво | `HlsPipelineBudgetTest`: hostile input, CR/LF/CRLF, три HTTP proxy-гілки, збереження leases, відмова flatten до headers; окремий повтор колишнього OOM у 128 MiB |
| R2 / P1 | `MediaSession` отримує `SessionPlayer`, а не raw engine. Play/Prepare/Stop і доступні команди залежать від актуальної ownership policy; Play проходить той самий VM intent, що UI | Справжній SDK `MediaController` у `SessionPlayerControllerTest`; VM-тести sleep expiry, Chromecast/DLNA, background і закриття |
| R3 / P1 | `ContentResolver.query` і отримання persistable grant виконуються на IO; loading публікується до metadata lookup; cancellation передається через `CancellationSignal` | `PlaylistDocumentNameTest`: потік не Main, блокувальний provider, cancellation, закриття cursor, відсутня metadata |
| R4 / P2 | Окремий власник впорядкованих durable writes; UI показує saving/failed/retry; Add закривається після успішного запису. Snapshot видаляється лише після commit source list | `PlaylistSourcePersistenceTest`: звичайний URL add, import, failed remove, restart, retry та re-add. `PlaylistSaveUiTest`: failed save не викликає success navigation |
| R5 / P2 | Ліміт джерел перевіряється до network/file load. Source sheet вимикає Add на межі, UI пояснює обмеження; оновлення вже відомого source ID дозволене політикою | 10 → 11 refusal без loading/network; capacity UI без success dismiss; historical oversized source list лишається доступним для вибору/видалення |
| R6 / P2 | Adapter публікує справжню можливість channel navigation навіть із singleton Media3 timeline; generic Next/Previous і MediaItem-варіанти мапляться однаково | Реальний `MediaController`: усі чотири команди, один dispatch, зміна boundaries і handback без перестворення session |

Основні точки реалізації:

- [HlsPlaylistBudget.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/proxy/HlsPlaylistBudget.kt:11>)
- [SessionPlayer.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/SessionPlayer.kt:29>)
- [PlaylistFileLoader.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/playlist/PlaylistFileLoader.kt:62>)
- [PlaylistSourcePersistence.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/PlaylistSourcePersistence.kt:30>)
- [PlaylistController.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/PlaylistController.kt:163>)
- [PlaylistAddCompletion.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/playlist/PlaylistAddCompletion.kt:14>)

### Додаткові стикові помилки, закриті під час реалізації

- Успішний reload міг стерти попередження про невдалий source save: reducer тепер зберігає
  незалежний durability outcome; додано regression.
- Швидкий import міг завершитися до того, як Compose побачить `isLoading=true`.
  Готовність до збереження тепер публікує controller (`sourceReadyToSave`), а не виводить UI
  з короткочасного loading edge. Є тест, у якому composition взагалі не бачила loading.
- Під час відмови додавання вже відомого source не можна видаляти його збережений snapshot.
  Cleanup pending import тепер перевіряє наявність ID у source list.
- Закритий download banner повертався після recreation: `LaunchedEffect(true)` скидав
  `rememberSaveable` dismissal. Зберігається також попередній active state; тест відновлює composition.

## UI

- Fatal player: прибрано порожню video area, повтор channel info, дубль нижнього Back
  та прямий audio control без локального відтворення. Retry/Next/Back залишаються явними;
  video surface повертається після виходу з fatal state. Сам engine не звільняється UI.
- Background downloads: компактний status у верхній панелі, деталі розгортаються окремою
  кнопкою; збережено dismiss і touch targets. Тести перевіряють розгортання/згортання та recreation.
- Нові повідомлення збереження/ліміту/деталей додано до EN/UK/RU/ES.
- Для повного inline fatal state додано **один новий** Roborazzi golden, переглянутий вручну;
  старі goldens у цьому проході не перезаписувалися. Layout assertions перевіряють viewport,
  єдині channel/Back labels, доступність дій та відсутність прямої Audio-кнопки.

[Новий візуальний еталон](<C:/UA CAST/app/src/test/screenshots/inline_player_fatal_cinema.png>)

## Перевірки

**Фінальний gate: BUILD SUCCESSFUL.**

```powershell
.\gradlew.bat :core:test --rerun-tasks --offline --no-daemon --console=plain
.\gradlew.bat :core:test :app:verifyRoborazziDebug :core:detekt :app:detekt :app:lintPlay :app:assembleDebug :app:assembleDebugAndroidTest :app:bundlePlay --continue --offline --no-daemon --console=plain
```

| Перевірка | Результат |
|---|---|
| App unit / Compose / proxy / session / screenshot suite | 2042 tests, 338 suites, failures/errors/skipped = 0 |
| Core, примусово перевиконано | 114 tests, 13 suites, failures/errors/skipped = 0 |
| Разом | **2156 tests, 351 suites**, нуль failures/errors/skips; net +26 tests проти аудиту |
| Roborazzi verification | PASS, включно з новим inline fatal golden |
| App + core Detekt | PASS; baseline залишається порожнім |
| Play lint | **0 errors, 0 warnings, 3 hints** |
| Debug APK, Android test APK, Play AAB | Зібрані; фінальний gate підтвердив їх актуальність |
| 14 repository guards | PASS; architecture, colors, glow, locale, docs, cast icon, baseline, premium, gating, logs, cleanup, legal, Play, fixtures |
| `git diff --check` | PASS; лише повідомлення Git про майбутню CRLF → LF нормалізацію в наявному dirty tree |

Core rerun: 19 s, 4/4 tasks executed. Фінальний загальний gate: 1 m 22 s,
148 tasks, 6 executed / 3 from cache / 139 up-to-date. App test task виконався,
а вже зібрані packaging/quality outputs не перебудовувалися без змін входів.

Логи: [повний gate](<C:/UA CAST/build/repeat-audit-fixes-release-gate.log>),
[fresh core](<C:/UA CAST/build/repeat-audit-fixes-core-fresh.log>),
[XML counts](<C:/UA CAST/build/repeat-audit-fixes-test-counts.json>),
[guards](<C:/UA CAST/build/repeat-audit-fixes-guards.log>).

Артефакти:

- [Debug APK](<C:/UA CAST/app/build/outputs/apk/debug/app-debug.apk>) — SHA-256
  `1B35DFEC11AAB969AB1E1C9B892FD90F2488FCA70C09CB5C7A914E84A0CFACA0`.
- [Play AAB](<C:/UA CAST/app/build/outputs/bundle/play/app-play.aab>) — SHA-256
  `B3B0A0A72C0CC43F41D5EC39B034D0E9CA3F89A4BB4EEE2254F10B8CDFB8B1D3`.

Три lint hints: target SDK 36 і повідомлення про новіші core-ktx/OkHttp. Це не доведені
вразливості та не автоматична вимога мігрувати target/залежності без compatibility tests.
Під час фактичного R8 build залишилися два повідомлення про відсутність async parsing
у AGP `OrderedClassFileResourceProvider`. Вони не приховані suppressions; AAB успішно зібрано.

### Повтор memory reproduction

Окрема JVM, `-Xmx128m`, той самий ASCII payload, що давав OOM у попередньому аудиті:

```text
MAX_HEAP=134217728
INPUT_CHARS=4000032
UNWRAP_REJECTED_WITH_IOEXCEPTION
PARSER_REJECTED_WITH_IOEXCEPTION
HEALTHY_AFTER_REJECTION_PASS
```

[Memory log](<C:/UA CAST/build/repeat-audit-fixes-memory128.log>).
Це 128 MiB перевірка unwrap/parser, а не вимір peak heap всього Android process.
Повні upstream → proxy HTTP-гілки перевірені окремими loopback tests зі звичайним test heap.

### Проміжні збої не приховувалися

- Виправлено помилки компіляції нових test fixtures, нові Detekt findings та Compose
  `ModifierParameter` lint. Suppressions/baseline для обходу цих перевірок не додавалися.
- Новий UI capture через Android `captureToImage` не отримував redraw у Robolectric
  (`WindowCapture.forceRedraw` timeout). Замінено лише механізм screenshot capture на
  наявний у проєкті Roborazzi; layout/action assertions не прибрано.
- В одному повному прогоні `ProxyManifestLeaseTest` отримав `SocketTimeoutException`
  на першому loopback response при одночасному R8/lint/test навантаженні. Той самий тест
  без змін і зі старим лімітом 5 s примусово повторено **тричі — 3/3 pass**.
  Наступний повний 2042-test app suite також PASS. Причина одиничного timeout не доведена;
  це не оголошується ані виправленим production
  багом, ані гарантовано нешкідливою випадковістю. Якщо повториться в CI, потрібні thread
  dump і phase timings у момент зависання, а не збільшення timeout навмання.

Логи повторів: [1](<C:/UA CAST/build/repeat-audit-fixes-proxy-repeat-1.log>),
[2](<C:/UA CAST/build/repeat-audit-fixes-proxy-repeat-2.log>),
[3](<C:/UA CAST/build/repeat-audit-fixes-proxy-repeat-3.log>).

## ARCHITECTURE CHECK — після змін

- Один playback owner: VM. Session adapter не має власного scope, engine або незалежної
  state machine; policy читається заново перед dispatch. Factory типом параметра забороняє
  випадково передати raw player. Release wrapper делегує release engine один раз.
- Playlist controller володіє наміром додавання та UI state. `PlaylistSourcePersistence`
  володіє ordering/commit/post-commit cleanup у вже наявному controller scope. UI не пише файли.
- HLS budget — чиста bounded policy; не новий runtime singleton/registry. Поріг однаковий
  для початкових manifests і refresh усередині flatten.
- SAF IO залишається у data loader. Cursor та cancellation мають явного власника;
  provider, який ігнорує cancellation, усе ще може затримати IO worker — це не обіцянка timeout.
- Напрямок залежностей не змінено; runtime player ↔ cast цикл не додано. Замість нарощування
  великого controller винесено фізичну persistence відповідальність.
- Старі тести видаленого `MediaSessionPlayerCommandDispatcher` замінено тестами реального
  SDK controller. Тести production contracts не відключалися.

## Що залишається до широкої публікації

1. Фінальні instrumented/lifecycle/media-button перевірки на authorized phone. У цьому
   проході `adb devices -l` порожній; встановлення чи обхід телефона не виконувалися.
2. Реальні Chromecast/DLNA/Hisense VIDAA: receiver rejects, Wi-Fi loss/reconnect, rapid switch,
   expired URL. Unit/loopback checks не доводять сумісність конкретного firmware.
3. Реальні Play Billing purchase/restore/refund/account-change перед увімкненням commerce.
   `STORE_IS_LIVE=false` не змінено; прихований premium store не оголошується поламаним.
4. Release-like frame/GC/heap measurements на слабкому пристрої, зокрема concurrent proxy,
   великий XMLTV та playlist. Хостові budgets не замінюють таких вимірів.
5. Final signed artifact, dependency SCA/SBOM та store declarations; CI remote run тут не запускався.
6. TalkBack/daylight перевірка наживо. Light/high-contrast theme — опційний product design,
   не підтверджений баг; ще одну схожу темну тему не додано.

Вердикт: R1–R6 виправлено, локальний gate зелений; збірка готова до контрольованого device/internal
тестування. **Широкий production rollout: ще NO**, поки
немає перелічених device/receiver/release доказів. Це не гарантія відсутності всіх майбутніх багів.

Версію не змінено. Публікацію, push і вимкнення комп'ютера не виконано.
