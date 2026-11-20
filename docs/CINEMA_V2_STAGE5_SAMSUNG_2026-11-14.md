# Cinema V2 — етап 5: Samsung та допоміжні екрани

Наведені фактично виконані зміни та перевірки; невиконані перевірки і залишкові ризики вказано наприкінці.

## Пристрій та захист даних

Samsung SM-M156B, Android 16 / API 36; фізичний дисплей 1080 × 2340 px, фізична щільність 450 dpi, наявне системне перевизначення 420 dpi. Масштаб системного шрифту на момент перевірки — 2.0. Параметри телефона не змінювалися. ADB-налагодження авторизоване.

Початково Android показував пакети `com.uacastplayer.debug` і `com.uacastplayer.debug.test`. Після переривання та повторного з’єднання ADB початковий debug-пакет перестав відображатися навіть у `pm list packages -u`, тому отримати його APK та порівняти сертифікат не вдалося. Інсталяції або видалення цього пакета з боку перевірки не виконувалися; причина зміни стану не встановлена.

Для тестів створено окремі `com.uacastplayer.debug.stage5qa` і `com.uacastplayer.debug.stage5qa.test`. Їхній application ID перевірено за metadata перед встановленням. Зміна ID здійснена лише opt-in Gradle init-скриптом [android-isolated-qa.init.gradle](../scripts/android-isolated-qa.init.gradle); звичайна конфігурація проєкту не змінена. Тести працюють із синтетичними плейлистами та app-private файлами QA-копії. Два тести, які змінюють системну гучність, виключені з фізичного прогону.

Перед роботою створено перевірену за SHA-256 копію 1710 наявних файлів, стану Git index, patch та списку 9 попередніх відсутніх шляхів: `build/cinema-v2-stage5-2026-11-14/before/`.

## Підтверджені виправлення

- Пошук батьківського контролю повертав два рядки з однаковою назвою й призначенням. Compose-тест спочатку відтворив два результати. Повторений тест підтвердив один результат, який відкриває потрібний розділ; решта сценаріїв пошуку також пройшла.
- В landscape 891×411dp зі шрифтом 200% пункти бічної навігації були поза видимою областю над мініплеєром, а назви переносились усередині слова. Компактний режим зберігає чотири іконки та їхні доступні назви, зони не менше 48dp; перевірка координат і перехід у налаштування пройшли на Samsung 4/4. [До](../build/cinema-v2-stage5-2026-11-14/nav-failure-diagnostic/settingsSearchShowsParentalDestinationOnce.png) / [після](../build/cinema-v2-stage5-2026-11-14/samsung-ui/landscape-rail-mini-verified.png).
- На Samsung в результаті пошуку підпис і назва однієї картки дублювалися. Перевірка у немергованому дереві семантики спочатку знайшла два тексти, після виправлення — один; повторений Samsung-скриншот підтверджує результат.
- При 200% системного шрифту Samsung кнопка відкриття допомоги була поза видимою областю, оскільки перед нею розташовувалися довгі службові описи. Дію переміщено на початок секції; той самий Android-тест після виправлення успішно відкрив довідку.
- Після швидкого виходу з fullscreen у landscape панель вбудованого плеєра за шрифту 200% була вищою за видиму область, і кнопка fullscreen опинялася поза екраном. Регресійний Compose-тест спочатку впав, після обмеження розміру панелі для широкого низького вікна — пройшов. Android-тест на Samsung повторно пройшов три переходи з декодованими кадрами та перевіркою збереження тих самих PlayerView/TextureView. Окремі сім тестів локального плеєра та detekt пройшли.
- Захист instrumentation-фікстур допускає тільки початковий debug package і конкретний QA package; додано негативні перевірки production та сторонніх package ID.

## Реальний Samsung: завершені перевірки

Початкові 12 native-сценаріїв Player/EPG/локального Media3-відео пройшли на QA APK до двох змін у налаштуваннях. Після цих змін повторено 10 сценаріїв: усі пройшли, включно з підтримкою/пошуком, мініплеєром, темами, Back та відкриттям DLNA. Фізичне підключення до Chromecast або DLNA-рендерера цими тестами не доведено.

Скриншоти синтетичного fixture на Samsung: [EPG](../build/cinema-v2-stage5-2026-11-14/samsung-player/epg.png), [плеєр](../build/cinema-v2-stage5-2026-11-14/samsung-player/player-cinema.png). Зображення містять тестові канали, а не відео реального провайдера.

На Samsung виконано синтетичне розбирання та групування M3U в debug ART: після розігріву 500 каналів три вимірювання для 40 000 каналів становили 922 / 826 / 765 мс; дельта алокацій процесу — приблизно 28 МБ на кожен прохід. Для 10 000 каналів: 272 / 234 / 237 мс. Це час parser + grouper, а не відкриття каталогу чи пошук у UI; peak heap і розподіл часу GC ці дані не замінюють. Під час прогону на комп’ютері паралельно виконувався Gradle. Сирі умови, алокації та GC — `build/cinema-v2-stage5-2026-11-14/samsung-playlist-performance.txt`.

Окремий замір 30 циклів приховування/показу контролів на Samsung із масштабом 200%: медіана 232 мс, p90 254 мс, PSS тестового процесу 221 652 КБ. Час включає синхронізацію тестів; процес не використовував декодер. Це не FPS, peak heap чи порівняння зі слабким пристроєм.

Три `am start -W -S` для ізольованої QA-копії з наявними синтетичними fixtures дали Android `LaunchState: COLD`, `TotalTime` 2060 / 1847 / 1800 мс. Три повернення існуючого процесу дали `LaunchState: HOT`, 85 / 81 / 83 мс. Це час запуску Activity, який повідомив Android, а не вимір повної готовності каталогу; окремого WARM-прогону та порівнянного попереднього baseline немає. Сирі результати — `build/cinema-v2-stage5-2026-11-14/samsung-startup.txt`.

У доступній історії `dumpsys activity exit-info` для QA-пакета останні 16 записів — завершення після instrumentation (`FORCE STOP`); записів про crash або ANR у цій обмеженій історії немає. Це не гарантія відсутності помилок поза перевіреним інтервалом.

Пасивний `dumpsys gfxinfo` після останнього запуску показав 16 кадрів, із них 4 janky (25%); одиничний `dumpsys meminfo` — PSS 178 964 КБ. Вибірка кадрів надто мала для висновку про плавність; PSS не є peak heap. Лічильники не скидалися, інтервал охоплює запуск QA-процесу. Сирі файли — `samsung-gfxinfo.txt` і `samsung-meminfo.txt` у build-каталозі етапу 5.

Ширший фізичний прогін: 229 сценаріїв, із них 219 успішних, 6 assumptions та 4 невдалі; два тести системної гучності виключено до запуску. Одна невдача — `fullscreenKeepsNativeViewsAndRendersAfterSurfaceRecreation`: після входу в fullscreen тест не знайшов кнопку виходу. Тест побачив декодовані кадри та збережені PlayerView/TextureView до цього кроку. Подальша діагностика виявила недоступну кнопку в горизонтальному вікні; після виправлення окремий повтор успішний. У повторному повному прогоні fullscreen пройшов; окремо лишилися чотири інші помилки навігації.

У цьому ж довгому прогоні два сценарії навігації налаштувань не знайшли очікуваний огляд / поле пошуку, хоча вони пройшли в окремому запуску 10 сценаріїв. Необхідно перевірити збережений стан між тестами та повторити їх після завершення всього прогону; причина наразі не встановлена.

Повторний повний прогін: 229 сценаріїв, 219 пройшли, 6 assumptions, 4 падіння навігації/пошуку; тести системної гучності виключені. Лог `samsung-full-native-after.log`. Шість пов’язаних тестів окремо пройшли 6/6. Серія з попереднім тестом примусової landscape-орієнтації одного разу дала 3 падіння з 9, а при повторі та під час запису кадрів — 9/9. Залежність від часу підтверджена, але остаточна причина поки не доведена. Експеримент із очікуванням початкової конфігурації після зняття примусової орієнтації дав timeout на Samsung і був повністю відхилений; він не входить у фінальний код. Діагностика: `samsung-predecessor-repro.log`, `samsung-predecessor-repro-repeat.log` та `nav-repro/`.

Додаткові реальні скриншоти Samsung зі шрифтом 200%: [головний екран Cinema](../build/cinema-v2-stage5-2026-11-14/samsung-ui/cinema-v2-stage3/home-theme-cinema.png), [каталог](../build/cinema-v2-stage5-2026-11-14/samsung-ui/cinema-v2-stage3/channels-cinema.png), [пошук у налаштуваннях після виправлення](../build/cinema-v2-stage5-2026-11-14/samsung-ui/settings-parental-search-final.png), [вибір пульта](../build/cinema-v2-stage5-2026-11-14/samsung-ui/remote-chooser.png), [DLNA-панель](../build/cinema-v2-stage5-2026-11-14/samsung-ui/ui-polish/player-dlna-picker.png). Останній кадр містить системну підказку до маркера перетягування, що з'явилась під час тесту; це не перевірка з’єднання з телевізором.

### Фінальний широкий прогін після останніх змін

`build/cinema-v2-stage5-2026-11-14/samsung-final-full.log`: 229 сценаріїв, 209 успішних, 6 assumptions, 14 невдалих; 2 тести системної гучності були виключені до запуску. Падіння: `bufferingPauseAndRapidTogglesFollowEngineIntent`, `theModeOutlivesThePlayerItWasSetIn`, `storageFailureOffersRetryAndRecoversTheLegacyFileWithoutRestarting`, вісім сценаріїв налаштувань і мініплеєра, `chooserOffersBothAppOnlyModes`, `minimizeKeepsTheAuthenticatedConnectionAndReopenDoesNotPairAgain`, `pinCreationFieldsCanBeLeftUsingPhoneDpad`. Точні назви та stack trace — у логу. Окремі шість перевірок fullscreen/пошуку/довідки/навігації перед повним прогоном пройшли 6/6 (`samsung-final-targeted.log`).

Після прогону виявлено, що передній план телефона зайняло системне вікно підтвердження оновлення Samsung. Воно могло перешкоджати UI-тестам і викликати переходи Activity у фон, але час появи та зв’язок із кожною помилкою не встановлено. Комп’ютер під час цього прогону паралельно виконував Gradle. Після того як користувач закрив системне вікно, всі 14 невдалих сценаріїв пройшли окремим повтором 14/14 (`samsung-final-failures-retry.log`). Це не доводить причину кожного падіння. Повтор усієї послідовності дав 220 успішних сценаріїв із 229, 6 assumptions і 3 падіння (`samsung-post-update-full.log`): `settingsHasOneHeadingAndBackReturnsThroughOverviewThenHome`, `settingsSearchShowsParentalDestinationOnce`, `miniPlayerAndDockRemainAdjacentAndTabChangesKeepOnePlayer`. Монітор 109 разів перевірив активне вікно та не побачив повернення вікна оновлення; короткочасну появу між перевірками він повністю не виключає. Три невдачі залишаються відкритими для аналізу.

### Після виправлення бічної навігації

Повний прогін остаточної QA-версії: 230 сценаріїв, **224 пройшли, 6 assumptions, 0 падінь** (`samsung-navigation-final-full.log`). Два тести, що змінюють системну гучність, не запускалися на Samsung. Серед пропусків: LAN-пошук без opt-in, два сценарії з необов’язковим тестовим плейлистом, TV playback та два Mi TV document-picker випадки, недоступні на цьому телефоні. Тест портретного нижнього dock і горизонтальної бічної навігації тепер перевіряє відповідний режим; новий сценарій перевіряє всі чотири пункти над мініплеєром. Системне вікно оновлення не з’явилося під час 111 періодичних перевірок; це не виключає появу між ними. Раніші падіння та умови збережено вище.

## Базова точка етапу 4

Емулятор Android 16: 223 успішні native-сценарії, 6 assumptions; debug 3132, release/Play по 2476 тестів; 115 screenshot-еталонів. Докладний [звіт етапу 4](CINEMA_V2_STAGE4_VERIFICATION_2026-11-14.md). Обидва тести системної гучності пройшли на емуляторі в цьому прогоні (`full-app-instrumented.log`); на Samsung вони виключені. Ці результати не приписуються Samsung і стосуються стану етапу 4.

Локальний Gradle-прогін етапу 5: debug 3133, release та Play по 2476 тестів без падінь чи пропусків; 115 screenshot-еталонів пройшли verify. `core` — 133 тести, decoder debug/release — по 5; частина незмінених задач використала Gradle cache. Lint debug/release/Play та detekt app/core пройшли; зібрані debug, release, Play APK та Play AAB. Повний лог: `build/cinema-v2-stage5-2026-11-14/full-gates.log`. Після цього додано локальний тест адаптивності плеєра й виправлення дубльованого підпису; фінальні gates повторюються. Попередній повний прогін після виправлення плеєра до останнього виправлення підпису: `final-gates.log` — debug 3134, release/Play по 2476, screenshot verify, lint/detekt і всі запитані збірки успішні.

## Фінальні локальні перевірки та APK

Після виправлення бічної навігації: debug unit — **3146/3146**, release і Play unit — по **2476/2476**, без failures/errors/skips; перевірка **115 screenshot-еталонів**, lint debug/release/Play, detekt app/core та збірки debug/release/Play APK і Play AAB завершилися успішно. `core` — 133 тести, decoder debug/release — по 5; незмінені задачі могли використовувати Gradle cache. Повний лог — `build/cinema-v2-stage5-2026-11-14/navigation-verified-gates.log`; перевірка архітектурних меж — OK. Збірка повідомляла про розбіжність версій SDK XML і попередження R8 щодо async parsing; збірку вони не зупинили.

[Universal debug APK 0.9.7](../build/cinema-v2-stage5-2026-11-14/artifacts/UA-Cast-0.9.7-stage5-universal-debug-navigation.apk), package `com.uacastplayer.debug`, versionCode 164, 37 473 754 байти, ABI arm64-v8a, armeabi-v7a і x86_64; перевірено ZIP CRC та SHA-256 `11052ABECC3A35F13541D4464D41CF810D4D110123821F00C5905BBC096F9A2A`. Це debug-збірка, не підписаний release APK для розповсюдження. На Samsung тестувалась окрема QA-копія з іншим application ID, не цей самий бінарний файл.

## Файли, змінені в етапі 5

Production UI: `ui/components/GlassNavigationRail.kt`, `ui/UiTestTags.kt`, `ui/player/PlayerInlineContent.kt`, `ui/settings/SettingsComponents.kt`, `SettingsOverview.kt`, `SettingsSupportSections.kt`. Unit-тести: `ui/layout/NavigationRailLayoutTest.kt`, `ui/player/LocalFilePlayerUsabilityTest.kt`, `ui/settings/SettingsOverviewTest.kt` у `app/src/test/kotlin/com/uacastplayer/`. Для ізольованих Android-тестів додано `testsupport/IsolatedPackageGuard.kt`, `IsolatedPackageGuardTest.kt` і opt-in `scripts/android-isolated-qa.init.gradle`.

Android-тести в `app/src/androidTest/kotlin/com/uacastplayer/`: `backup/BackupEpgSelectionInstrumentedTest.kt`, `BackupExportStopInstrumentedTest.kt`, `SecurePortableBackupInstrumentedTest.kt`; `player/PlayerLifecycleInstrumentedTest.kt`; `playlist/PlaylistMigrationCommitInstrumentedTest.kt`, `PlaylistMigrationRecoveryUiInstrumentedTest.kt`; `ui/UiPolishInstrumentedTest.kt`, `ui/components/ChannelArtworkInstrumentedTest.kt`, `ui/remote/RemoteAccessibilityInstrumentedTest.kt`, `ui/settings/IconSourceDraftInstrumentedTest.kt`, `ui/tv/TvConfirmationDialogInstrumentedTest.kt`, `TvEmptyModalInstrumentedTest.kt`, `TvLauncherBannerInstrumentedTest.kt`. Частина змін цих тестів лише дозволяє перевірений окремий QA package; вони не надають доступу до production package. Звіт — цей файл.

Передетапні файли збережено в контрольному ZIP; перевірка підтвердила незмінні HEAD та index і відсутність втрачених файлів. Зміни попередніх етапів і користувача в робочому дереві збережені; комітів або публікації не виконувалося.

## Залишилося

- Повний візуальний огляд усіх підрозділів, порожніх та помилкових станів на 320dp і 200% шрифту; наявність загальної теми або успішного тесту окремого компонента не доводить, що кожен екран переглянуто.
- Реальні з’єднання Chromecast і DLNA та перевірка Android TV з D-pad на фізичному телевізорі не виконані. Показ панелі DLNA й тести TV-компонентів на телефоні не є такою перевіркою.
- На Samsung не запускалися два тести, що змінюють системну гучність; раніше вони пройшли на емуляторі етапу 4.
- WARM startup, peak heap, frame timing на достатній вибірці, CPU, частота рекомпозицій, час відкриття великого каталогу й пошуку та перевірка витоків залишилися без надійного вимірювання. Синтетичний parser benchmark не замінює цих вимірювань.
- Раніше був одноразовий збій перевірки паузи під час буферизації в прогоні, коли на телефоні виявлено системне вікно; наступні окремий та повний прогони пройшли. Точний причинний зв’язок не доведений.
- Потрібні перевірки релізного підпису, оновлення без втрати даних і приймальне тестування реального медіаконтенту перед публікацією. Фінальний debug APK не є релізною збіркою.

Етап 6 не розпочато. Повного твердження, що всі можливі помилки відсутні, ці результати не підтверджують.
