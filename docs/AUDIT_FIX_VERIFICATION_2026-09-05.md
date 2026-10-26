# Виправлення після глибокого аудиту — 05.09.2026

## Межі цього звіту

Це звіт про перевірені виправлення в поточному робочому дереві, а не обіцянка відсутності всіх можливих дефектів. Початкові незакомічені зміни користувача збережено. Коміти, публікацію, увімкнення реальних платежів і вимкнення комп'ютера не виконували.

Перевірено наскрізні шляхи: імпорт → збереження → відновлення → UI; канал → Media3 → повторне відновлення → завершення; локальний плеєр ↔ Cast/DLNA; proxy → upstream; імпорт резервної копії → фактичне завершення запису. Для регресій використано unit/Robolectric, локальні HTTP/SOAP fixtures і фізичний Mi A2. Це не повний ручний прохід кожної кнопки та не випробування реального телевізора.

## Виправлені проблеми

Пріоритети нижче характеризують ризик **до виправлення**, а не відкриті блокери.

### 1. Відтворення та пам'ять

- **P1: явні TS/progressive-потоки примусово трактувалися як HLS.** `StreamMimeClassifier`, `MediaItemFactory`, `CastContentType`: розділено HLS, DASH, MPEG-TS та progressive; для Cast передаються відповідні MIME. Для URL без визначеного формату після помилки розбору HLS дозволено одну спробу progressive, без циклічної зміни форматів і без переписування підписаного URL. Явні `.m3u8` не маскуються цим fallback.
- **P1: поріг байтів буфера Media3 міг поступатися часовому порогу.** `PlayerEngineFactory.buildLoadControl`: пріоритет віддано розміру буфера; профілі 8/16/24 MiB залишено. Регресія використовує справжній `DefaultLoadControl` і allocator. Це не обмеження всієї пам'яті процесу: декодери, UI, EPG і кеші рахуються окремо.
- **P1: нескінченне відновлення мовчазно завислого live-потоку.** `StallRetryPolicy`, `PlayerSessionStateMachine`, `PlayerViewModel`: максимум 8 автоматичних спроб на серію невдач; після вичерпання зупиняється upstream і показуються наявні дії повтору/вибору каналу. Бюджет скидають явний повтор, зміна каналу або 60 секунд спостережуваного поступу. Час очікування, buffering і короткий READY не вважаються успішним відтворенням. Годинник детектора — monotonic; grace охоплює backoff плюс prepare. Індикатор відновлення більше не зникає лише тому, що buffering ще не досяг порога зависання.
- **P2: таймер сну належав поточному Compose-екрану.** `PlayerSleepTimer` тепер належить ViewModel, переживає перехід mini/fullscreen та зміну конфігурації; повне закриття playback скасовує таймер. Перевірено локальне відтворення; поведінку таймера на віддаленому receiver цей пункт не гарантує.

### 2. Chromecast

- **P1: suspension/resume та застарілі SDK-події могли розсинхронізувати ownership.** `CastSessionRepository`, `CastSessionLifecycle`, `CastSuspensionPolicy`: перевіряється власник SDK-сесії; під час suspension зберігаються активний канал/proxy та віддалене володіння відтворенням, але припиняються старі recovery/load/watchdog jobs. Є 120-секундний timeout. Відновлена здорова сесія з тим самим контентом не перезавантажується без потреби; зміна каналу під час suspension застосовується після resume.
- **P1: результат старої діагностики міг змінити нове завантаження.** Результати direct-probe прив'язані до generation і delivery mode. Синхронна відмова load не запускає поверх завершеного fallback ще один probe/watchdog.
- **P1: відмова proxy/load могла лишити локальний плеєр зупиненим.** `CastLoadResultReducer`, `CastProxyFailureReducer`, `PlayerCastMapping`: термінальна невдача завершує proxy/recovery; стан підключення SDK відділено від ownership успішного/активного playback. StateFlow виконує роль резервного механізму синхронізації, коли одноразовий side effect пропущено.
- **P1: постійна помилка receiver могла утримувати recovery і ресурси нескінченно.** `CastRecoveryPolicy`: 3 швидкі спроби, потім повільні, загалом не більше 8; новий канал або хвилина стабільного PLAYING відновлюють бюджет.

### 3. DLNA

- **P1: локальний upstream міг конкурувати з receiver за єдине дозволене з'єднання провайдера.** `PlayerDlnaController` перед початком підключення синхронно передає ownership; `isConnecting` також означає, що локальний player має відступити. Після невдачі відновлення локального playback враховує background-політику.
- **P1: захищені канали втрачали User-Agent/Referer.** `DlnaSessionRepository` переносить метадані каналу через connect/switch до `proxy.registerPlaylist`; HTTP/SOAP інтеграційна регресія перевіряє заголовки на upstream.
- **P1: швидка зміна каналу/stop під час першого connect могли не стосуватися поточної спроби.** Команди враховують pending renderer; stop охоплює pending і connected пристрої. Generation захищає нове підключення від старих мережевих callback.
- **P1: невдалий repoint після Stop міг залишити неправдивий connected state.** Невдале підключення/перемикання очищає стан і proxy замість збереження старого активного renderer.
- **P1: вимкнений телевізор або renderer, що завис у TRANSITIONING, міг залишатися «активним».** `DlnaTransportStateReader` + `DlnaRendererWatchdog`: перевірка transport state кожні 20 секунд; 3 послідовні нездорові відповіді завершують з'єднання. Успіх скидає лічильник. Власник скасовує job. Для renderer без GetTransportInfo watchdog вимикається, а не оголошує несумісний пристрій мертвим.

### 4. Плейлисти, EPG і кеш

- **P1: HTTP 200 із пошкодженим XML/gzip EPG міг викидати помилку поза очікуваним outcome.** `EpgRepository`: помилка читання повертається керовано; cancellation не поглинається; тимчасовий файл прибирається, останній коректний snapshot зберігається.
- **P1: відсутній кеш збереженого джерела міг залишити порожній старт.** `PlaylistController.loadInitialSource`: якщо snapshot відсутній, завантажується збережений URL/файл.
- **P1: невдала зміна джерела могла показати канали A під назвою B.** Вибір іншого джерела очищає попередній source-specific стан. Невдалий refresh того самого джерела зберігає його старі канали.
- **P1: порожнє оновлення могло замінити придатний snapshot та обнулити metadata джерела.** Порожній результат більше не перезаписує добрий snapshot і не викликає успішний `onLoaded` із нульовим набором.
- **P1: clear cache змагався з незавершеними записами.** `PlaylistSnapshotMutationCoordinator` використовує загальну generation і взаємне виключення фізичного запису/очищення. EPG також очищається через свій координатор. Завершення lease у `finally` не залишає назавжди активні записи після failed/cancelled download.
- **P2: незавершений EXTINF міг передати заголовки/групу наступному каналу.** `M3uParser` скидає metadata покинутого запису; підтримка options перед першим EXTINF збережена.
- **P2: зайве важке перетворення snapshot на Main.** Flatten для snapshot перенесено в CPU-dispatcher.

### 5. Обране та резервні копії

- **P1: відтворення захищеного каналу з обраного могло втрачати UA/Referer; логотипи й EPG metadata також губилися.** `FavoriteChannel`, `FavoritesJsonCodec`, `BackupFavorite`, `BackupCodec`, `BackupMergePolicy`: наскрізно збережено `tvgName`, `tvgLogo`, `userAgent`, `referrer`. Старі записи читаються завдяки default-полям. Дозаповнення metadata зі свіжого плейлиста зіставляє точний stream URL, а не випадково однакові назви різних провайдерів.
- **P1: імпорт міг повідомити про успіх до фактичного запису sources/favorites.** `LatestValueWriter.awaitPending`, `FavoritesRepository.awaitPersistence`, `PlaylistController.awaitSourcesPersistence`, `BackupController`: звіт про імпорт очікує завершення запису. Disk failure дає локалізоване повідомлення про незбережені дані. Скасовані/старі імпорти не публікують новий результат.
- **P1: міграція могла прибрати старе джерело після невдалого нового запису.** Legacy-дані відкидаються лише після успішного збереження.
- **P2: на порожній інсталяції імпорт sources міг не зробити жодне джерело доступним для перегляду.** Actual backup-import активує джерело за потреби; дія вибору джерела лишається доступною на порожньому Home.

### 6. Premium, proxy і безпека тестів

- **P1 для майбутнього live billing: пізній callback покупки A міг завершити очікування нової покупки B.** `PendingPurchaseCoordinator` не замінює невизначену спробу після timeout/cancellation; перевіряє product і власника launch failure. Пізні purchase updates усе ще можуть оновлювати entitlement. Реальні платежі залишені вимкненими: `PremiumAvailability.STORE_IS_LIVE = false`.
- **P1: однакові proxy URL з різними заголовками могли отримати спільний ресурс/неправильну авторизацію.** `ProxyResourceRegistry` включає type, URL, UA і Referer у однозначно закодований ключ.
- **P1: після перших байтів HLS→TS replay міг нескінченно опитувати 403/frozen manifest без нового контенту.** `HlsReplayProgress`: 60-секундний бюджет без поступу, після чого committed response завершується. Перевірено frozen manifest і відмову після першого сегмента. Це перевірка на межах циклу плюс мережеві timeouts, не жорстка гарантія рівно 60 секунд для будь-якого socket write.
- **P1 для QA-даних: запуск instrumented fixtures на фізичному телефоні міг видаляти користувацькі debug-плейлисти.** Bash-runner тепер відмовляється робити це без явного opt-in. Новий `run-preserved-device-tests.ps1` створює приватний recovery archive та повертає `files/shared_prefs` у `finally`. APK визначається через metadata і перевіряється **до** зупинки/переміщення даних. Архіви містять потенційно приватні дані: вони лишаються в ignored `app/build/device-audit`, не призначені для публікації.
- **P2: architecture gate міг хибно пройти без grep.** Скрипт розрізняє «немає збігів» і помилку інструмента, перевіряє наявність grep. Негативний тест відсутнього grep повертає код 2, а не успіх.

## Перевірки

Остаточний спільний Gradle-прогін після останньої зміни: **BUILD SUCCESSFUL**, 1 хв 42 с, 142 tasks (27 executed, 115 up-to-date).

| Перевірка | Результат |
| --- | --- |
| `testDebugUnitTest` | **2011 tests, 0 failures, 0 errors, 0 skipped**; на початку цього циклу було 1969 |
| `detekt` | **0 порушень**; baseline залишився порожнім, нових suppressions не додано |
| `lintPlay` | **0 errors, 0 warnings, 11 hints** щодо target/версій інструментів і залежностей |
| `assembleDebug` + `assembleDebugAndroidTest` | Успішно |
| `bundlePlay` з R8 | Успішно; два попередження про async parsing у class-file provider AGP залишилися |
| Architecture dependency checks | Успішно; окремо підтверджено відмову при відсутньому grep |
| Bash і PowerShell syntax checks | Успішно |
| `git diff --check` | Без помилок whitespace; Git окремо попереджає про майбутню нормалізацію CRLF у двох файлах |
| Mi A2, API 30, останній APK | **13/13**, 70,555 с; початкові debug `files/shared_prefs` відновлено |

Остаточний AAB: `app/build/outputs/bundle/play/app-play.aab`, **19 372 872 байти**, SHA-256 `AACB3556A8A04832C6FF081957096FAD35C26A882A740C0630C989EC60241F3B`. ZIP відкривається, містить manifest, 2 DEX і 18 native libraries; R8 mapping збережено в `app/build/outputs/mapping/play/mapping.txt`. Це перевірка локального артефакту, не підтвердження прийняття Google Play або перевірка всіх ABI на пристроях.

Телефонна серія охоплює 9 lifecycle-тестів плеєра та 4 тести video fit: повторне відкриття/закриття, mini/fullscreen, зміни конфігурації й каналу, pause/background/return, sleep timer та режими відображення. Вона не замінює перевірку всіх екранів і зовнішніх сервісів.

Локальний протокол останнього device-прогону: `app/build/device-audit/instrumented-8c8a54ee38704996a55f67552bba268d.txt`. Recovery archive і збережені fixture-дані лишено для можливого відновлення; не публікувати їх разом зі звітом.

Після останнього прогону додатково прочитано архів відновлених `files/shared_prefs` із телефону: його SHA-256 збігається з архівом до тестів. Початкові файли й налаштування підтверджено повернутими байт у байт, без виведення приватного вмісту.

Основна команда:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:detekt :app:lintPlay :app:assembleDebug :app:assembleDebugAndroidTest :app:bundlePlay --continue --offline --no-daemon --console=plain
```

Телефон тестується через захищений runner, а не команду, що видаляє дані після instrumented run:

```powershell
.\scripts\run-preserved-device-tests.ps1 -AdbPath '<Android SDK>/platform-tools/adb.exe' -Serial '<Mi A2 serial>'
```

Ключові нові регресії: `MediaItemFactoryTest`, `PlayerLoadControlTest`, `PlayerSleepTimerTest`, `PlayerStallBudgetTest`, `EpgMalformedDocumentTest`, `PlaylistRecoveryTest`, `BackupDurabilityTest`, `FavoriteMetadataTest`, `DlnaSessionLifecycleTest`, `DlnaRendererWatchdogTest`, `CastSuspensionPolicyTest`, `HlsNoProgressTest`, `ProxyResourceIdentityTest`, `PendingPurchaseCoordinatorTest`. Розширено також codec, writer, mutation coordinator, reducer і state-mapping тести.

## Що залишається перед широким production-релізом

1. **Реальний Hisense VIDAA та Chromecast.** Fixtures не доводять підтримку codec/container прошивкою телевізора. Потрібні захищені TS/HLS, 20–30 швидких перемикань, вимкнення ТВ, Wi-Fi drop/reconnect і тривалий soak. Телевізійне «Archivo no compatible» не можна чесно оголосити гарантовано усуненим без цього.
2. **Google Play.** Потрібні license testers, pending/cancel/restore/refund, acknowledgement, перевірка фактичних products і pre-launch report. У цій збірці store flag вимкнений. Консервативний захист невизначеної покупки може вимагати перезапуску застосунку, якщо Play взагалі не доставить callback; це свідоме обмеження, не перевірена ідеальна UX-поведінка.
3. **Матриця Android.** Свіжий фізичний прогін тут — Mi A2, API 30. Не проведено новий device-прогін на всіх API 24–36 і Samsung у цьому циклі.
4. **Вимірювання продуктивності.** Потрібні actual baseline profile/Macrobenchmark, heap/GC/часи на слабких пристроях для 100 000 каналів та 300–400 тисяч XMLTV-програм. Коректний byte budget Media3 не є доказом відсутності OOM у всьому процесі.
5. **Інструментарій збірки.** R8 видає два `Class file resource provider does not support async parsing` від AGP provider. Вони не блокують AAB, але не приховані й не «виправлені» suppression. Lint version/target hints потребують окремої перевірки сумісності перед оновленням залежностей; актуальний повний vulnerability scan усіх залежностей тут не заявляється.
6. **Свідомо обмежений функціональний обсяг.** Sleep timer перевірено для local playback; watchdog старих DLNA renderer без GetTransportInfo вимикається; імпорт backup не є єдиною транзакцією між settings/sources/favorites; повний візуальний/accessibility аудит кожної кнопки тут не виконувався.
7. **Архітектурні межі — виправлено окремим наступним кроком.** Виділено production JVM-модуль `:core` (23 файли, 114 незалежних тестів); sampler/recovery плеєра, Cast recovery, життєвий цикл proxy і докази сумісності отримали окремих власників. `CastSessionRepository` проходить detekt без колишнього `LargeClass`-винятку. CI та архітектурні перевірки охоплюють обидва модулі. Це не оголошення всієї архітектури ідеальною: подальша feature-модульність і огляд інших великих класів залишаються поступовою роботою. Деталі — `docs/ARCHITECTURE.md`.

### Перевірка після виправлення пункту 7

- **2023 тести**: 1909 у `:app:testDebugUnitTest` + 114 у `:core:test`; 0 failures/errors/skipped. До цього кроку було 2011; додано 12 регресій, старі тести збережено.
- **13/13 на Mi A2**, 70,653 с. Debug-плейлисти й налаштування повернуто захищеним runner. Протокол: `app/build/device-audit/instrumented-d329f088b1174a9a82525586e358f69b.txt`.
- Detekt обох модулів: **0 порушень**, baseline порожній. Architecture, locale-format і documentation-reference checks пройшли.
- APK/AAB зібрані, `lintPlay`: 0 errors/warnings, ті самі 11 hints. Два попередження R8 про class-file provider залишаються.
- Новий AAB SHA-256: `0CE9D1D2062F88E51F34DFA50E2000FD330C91BA62D647E957A2C3554AD8510C`. Раніший hash у цьому звіті належить збірці **до** архітектурного кроку.
- GitHub workflows оновлено; віддалений CI у цьому кроці **не запускався**.

## Вердикт

Виправлено конкретні локально підтверджені дефекти; регресії перевіряють і відмови, не лише happy paths. Підстав оголошувати «все гарантовано виправлено й перевірено на всіх пристроях» немає. Для широкого релізу на 100 000 користувачів безумовне **YES** поки не обґрунтоване: бракує реальних receiver/Play і багатоверсійних device-перевірок, перелічених вище.
