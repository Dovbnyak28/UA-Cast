# Полірування пошуку каналів — 2026-10-03

## Результат і межі

Цей етап зосереджено на підтвердженій затримці скасування CPU-пошуку, а не на масовому
переформатуванні чи переписуванні працюючого плеєра. Простежено шлях від Compose-поля й
debounce через `produceState` до спільного playlist CPU dispatcher і чистих matcher-функцій.
Перевірено обидва phone-сценарії, правила matching, обмеження результатів і переданий
канал. Попередні PIN, TV, Premium, backup та icon-pack зміни збережено.

Це не повний повторний аудит кожного рядка й не обіцянка нульової кількості багів.
Версія, залежності, detekt baseline, дозволи, ліцензії й формат плейлистів не змінювалися.
Нових виконавців, глобальних coroutine scopes, кешів чи фонових таймерів не додано.

## Підтверджена проблема: P2 — скасований пошук продовжує весь обхід

Два шляхи одного дефекту:

1. `ui/channels/GroupsOverview.kt`, `GroupsOverviewGrid`: після debounce новий запит
   перезапускає producer і скасовує попередній. Уже запущений
   `withPlaylistCpu { ChannelSearch.search(...) }` не перевіряв cancellation під час обходу.
2. `ui/channels/ChannelListSection.kt`, `SingleGroupChannelList`: аналогічна поведінка для
   `channels.filter { displayName.contains(...) }` окремої групи.

Використовується один CPU-потік з `data/playlist/PlaylistCpuDispatcher.kt`, спільний з
обробкою плейлистів. Застаріла робота займала цей потік до кінця; наступний пошук або
обробка джерела мусили чекати. Це підтверджена зайва CPU-робота й потенційна затримка,
**не доведений ANR або публікація неправильних результатів**. `withContext` і раніше
відхиляв повернення результату скасованої корутини; це окремо перевіряється тестом.

### Відтворення до виправлення

Спочатку scheduling-код винесено без зміни поведінки в
`data/playlist/PlaylistChannelSearch.kt`; обидва UI-виклики використовували його.
`PlaylistSearchCancellationTest` застосовує справжній наявний CPU dispatcher, керовані
CountDownLatch, лічильник читань і скасування producer після початку першого читання.
Далі очікує завершення, перевіряє непублікацію й можливість наступного запиту.

Перший прогін: **3 тести, 2 падіння**. Обидва cancellation-тести отримали точне повідомлення
`cancelled work read 100000 channels`. Порожній фільтр проходив. Before-fix XML збережено
локально в ignored `app/build/code-polish-search-20261003/before-fix-search-cancellation.xml`.

100 000 — синтетичний algorithm stress-list. Production M3U importer і далі обмежений
50 000 каналів; цей тест не стверджує, що імпорт 100 000 дозволений.

### Виправлення

- `ChannelSearch.search` приймає optional cancellation probe, як наявні parser/filter policies.
  Перевіряє його перед роботою, між групами й кожними 256 каналами.
- Довгі displayName/tvgName, довгий майже збіжний query-prefix і довгі пробільні послідовності
  також мають перевірки всередині обходу символів із інтервалом 1 024.
- `searchPlaylistChannels` і `filterPlaylistChannels` задають єдину scheduling/cancellation
  межу для phone UI через `withPlaylistCpuCancellable`. Pure policies не залежать від Compose
  чи coroutine API; probe надходить від власника запиту.
- Фільтр окремої групи використовує наявний `ChannelListFilter` з порожніми groups/hidden keys;
  це зберігає displayName-only substring matching і не додає до нього tvgName чи whitespace
  normalization глобального пошуку.
- Відсутній запит і далі повертає наявний список окремої групи без копії та сканування.

Після виправлення ті самі integration assertions проходять: після скасування прочитано
не більше **257** каналів, старий запит не публікується, новий запит виконується на тому
самому CPU-потоці. Pure-тест також фіксує точні 257 читань. Це межа виконаної тестової
роботи, не замір FPS, heap чи гарантований millisecond timeout на будь-якому пристрої.

## Що навмисно не змінено

- Глобальний пошук: displayName або tvgName, case folding, whitespace normalization,
  порядок груп/каналів, ліміт 200 і відмінність рівно 200 від понад 200 результатів.
- Приховані групи лишаються доступними в глобальному phone search, але не в overview.
  Це попередня явна політика; її не уніфіковано з TV приховуванням.
- Пошук однієї групи: лише displayName без collapse внутрішніх пробілів, порядок і дублікати.
- Debounce, збереження query/scroll state і показ попередніх результатів під час нового scan.
- Формат/межі M3U, callback відкриття каналу та життєвий цикл Media3.

Перевірено JVM bytecode `ChannelSearch.appendMatches`: Kotlin lowering використав
звичайний iterator і index-counter; немає `IndexedValue` allocation/`withIndex` виклику
для кожного каналу. Окремого heap-профілювання не виконано, тож це не звіт про peak memory.

## Регресійні тести

Додано **11 host-тестів і 2 instrumented-тести**:

| Набір | Перевірка |
| --- | --- |
| `PlaylistSearchCancellationTest`, 3 | Обидва скасовані сканування на реальному CPU dispatcher; порожній фільтр без обходу |
| `ChannelSearchCancellationTest`, 6 | Blank query, batch, empty groups, довга назва, matching-prefix, whitespace-run |
| `PhoneChannelSearchUiTest`, 2 | Приховані групи/tvgName/пробіли/очищення; displayName-only/дублікати/clicked object |
| `PhoneChannelSearchInstrumentedTest`, 2 | Ті самі користувацькі сценарії на Android, без реального playlist/PIN/purchase |

Цільова регресія: **37 тестів, 6 suites, 0 failures/errors/skips**, app detekt, debug і
AndroidTest APK — успішно. Вона включає наявні `ChannelSearchTest`, `ChannelListFilterTest`
і `GroupsOverviewScrollTest`, а не лише нові тести.

Один проміжний compile-прогін нових UI-тестів знайшов відсутній import `assertCountEquals`;
його додано й весь цільовий прогін повторено успішно. Тести не вимикалися, assertions
не послаблювалися; нових suppressions і screenshot goldens немає.

Усі 15 верхньорівневих `scripts/check-*.sh` пройшли. `git diff --check` пройшов, лише
наявні попередження про можливе CRLF→LF в інших двох файлах.

## Фінальні перевірки

| Перевірка | Результат |
| --- | --- |
| Debug unit/UI + screenshot verification | 2 463 тести, 412 suites; 0 failures/errors/skips |
| Release unit | 2 174 тести, 348 suites; 0 failures/errors/skips |
| Play unit | 2 174 тести, 348 suites; 0 failures/errors/skips |
| Core | 126 тестів, 15 suites; незмінні успішні результати використано повторно |
| App/core detekt | Пройшли, порожні XML-звіти; baseline не змінено |
| Debug/Release/Play lint | 0 errors, 0 warnings; по одному наявному `OldTargetApi` Hint |
| 15 top-level checks | Пройшли |
| Check-script regressions | Doc references: 5 сценаріїв; baseline: 8; пройшли |
| Mi A2, Android 11/API 30, повторний прогін | **19 тестів, 0 failures/ignored; 86,487 с** |
| Debug, AndroidTest, signed universal Release builds | Пройшли; release-підпис перевірено |

Повний gate: **BUILD SUCCESSFUL за 11m 20s**, 203 задачі (43 виконано, 160 up-to-date),
configuration cache reused. Виконано:

```text
:app:assembleDebug :app:assembleDebugAndroidTest :app:detekt :core:test :core:detekt
:app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest
:app:lintDebug :app:lintRelease :app:lintPlay :app:assembleRelease
-Puacast.requireSigning=true --offline --max-workers=1 --no-daemon --continue
```

Після нового release build ще раз пройшли legal-assets для всіх 4 APK,
version-code ordering і Play distribution перевірки.

Кількості build variants — повторні виконання багатьох тих самих тестів, а не сума
унікальних випадків. Core source цього етапу не змінювався; up-to-date не видається за
нове виконання всіх core-тестів.

### Нативний прогін: помилки setup не приховано

Перший запуск тих самих 19 тестів на Mi A2: **6 failures, 252,122 с**. Два нові browsing
тести й чотири наявні player-тести не отримали Compose hierarchy (частина з timeout на
setup). Це сталося до відповідних assertions пошуку/плеєра. Інші 13 тестів пройшли.

Перевірено screen/keyguard/power state і crash-повідомлення саме тестового процесу:
після прогону телефон був unlocked/awake; у вибраному AndroidRuntime log не було crash.
Ці спостереження не доводять стан телефона протягом усього першого прогону, тому точна
причина setup-падінь **не встановлена**. Вони не записані як виправлений app crash чи
доказ того, що пристрій був заблокований.

На підтверджено awake/unlocked пристрої ті самі APK і 19 тестів повторено, без будь-яких
production/test правок, ignores, послаблення assertions або збільшення timeout: **OK (19 tests)**.
Якщо setup-проблема повториться, окремо дослідити cold Activity launch, Compose root
registration та зовнішні lifecycle/display-події; одиничний успішний повтор не гарантує
відсутності всієї environment flakiness.

Native-набір: `PhoneChannelSearchInstrumentedTest` (2),
`PlayerLifecycleInstrumentedTest` (15), `PlayerRestorationRegressionInstrumentedTest` (2).
Логи обох прогонів збережено локально:

- failure: `app/build/device-audit/instrumented-a774fd1abe84482088107ae031bf9c7d.txt`;
- passed repeat: `app/build/device-audit/instrumented-7046f8c7a0174718b4d0dcab1e109627.txt`.

Runner зберіг/відновив files і shared_prefs `com.uacastplayer.debug` після **обох** запусків.
Основний `com.uacastplayer` не перевстановлювався й не очищався. Приватні recovery tar
архіви не можна публікувати разом з результатами тестів.

Два наявні R8 warnings про `ClassFileResourceProvider` async parsing залишаються
toolchain diagnostics; не приховувалися suppression. Offline-прогін не підтверджує
актуальні Play target API-вимоги.

## Фінальний локальний APK

- Файл: `app/build/outputs/apk/release/app-universal-release.apk`.
- VersionName **0.9.7**, versionCode **164**, без підняття версії цього етапу.
- Розмір **24 177 560 байтів**, записано **2026-10-03 13:41:13 Europe/Paris**.
- SHA-256: `93426051232525BDEFD12D8123C36377EFDA10D2D3028E3B0BAFF3F6280F72EB`.
- `apksigner verify --verbose --print-certs`: **Verifies**, один signer, v2/v3 valid.
- SHA-256 сертифіката:
  `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`, наявний release-підпис.

Це новий артефакт цього етапу; SHA-256 попередніх звітів описують попередні збірки.

## Межі релізного висновку

Підтверджені два шляхи cancellation-дефекту виправлено. Цей етап не перевіряє реальний
Hisense VIDAA renderer, Google Play checkout, усю Android API-матрицю або тривалі
playback/heap/battery бюджети. Вони лишаються окремими release acceptance-перевірками.
Публікацію, version bump, встановлення release поверх основної програми й вимкнення
комп'ютера не виконано.
