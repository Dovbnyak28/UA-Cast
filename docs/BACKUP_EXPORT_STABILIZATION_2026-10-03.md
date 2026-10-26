# Стабілізація експорту резервних копій — 2026-10-03

## Межі

Перевірено збереження даних і cancellation, з підтвердженими виправленнями у
backup export. Шлях: Settings → SAF CreateDocument → AppViewModel →
BackupController → capture локальних плейлистів → JSON → output provider → UI result.
Прочитано також source/favorite/group/parental stores та icon-source storage;
їх не переписано без підтвердженого дефекту. Наявні сторонні зміни збережено.

Не змінено UI, політику Lite/Premium, дозволи, залежності, versionName/versionCode,
формат backup v2, ліміт 8 MB, embedded local playlists або merge/restore semantics.
Це цільове bug-hunting виправлення, не сертифікація кожного рядка репозиторію.

## P1 — паралельні експорти в один документ псують кінцеву копію

Файл: `app/src/main/kotlin/com/uacastplayer/app/BackupController.kt`.
Функції: `launchExport`, `writeBackup`.

До виправлення `exportGeneration` відкидав застарілий UI result, але не координував
записи. Користувач міг повторно вибрати той самий документ, поки перший provider
ще писав. Обидва jobs відкривали `openOutputStream(uri, "wt")` і записували JSON
незалежно. Старий handle міг дописати bytes після завершення новішого.

Відтворення використовує Robolectric ContentResolver та справжні незалежні
`FileOutputStream(file, false)` handles у тестовому cacheDir. Перший запис зупинено
після 8 bytes; друга коротша копія закінчує запис, тоді перший дописує свій tail.
Перед виправленням кінцевий JSON містив **старі** settings SMALL замість нових LARGE,
хоч новий експорт був успішний. Третій queued-request сценарій відкривав **3 handles
замість 2**. Відтворено також двома controller owners. Це реальні bytes тестового
файлу, не лише mock UI state; native/cloud SAF providers цим прогоном не тестувалися.

`BackupExportCoordinator` тепер дає cancellable coroutine Mutex для кожного URI.
Process-wide instance координує й різних controller owners, не утримуючи Context,
Activity чи completed jobs. Один handle має закритися до відкриття наступного.
Generation повторно перевіряється після очікування, тому obsolete queued request
не обрізає документ. Різні URI залишаються незалежними: наявний тест із повільним
і швидким документами проходить без зміни assertions.

Виправлення стосується **того самого URI у цьому процесі**. Воно не координує
іншу програму/процес або різні URI aliases одного фізичного документа. Провайдер,
який назавжди завис і не реагує на close, може затримати чергу того самого URI;
паралельний destructive write не є безпечним обходом такого зависання.

## P1 — скасований capture може відкрити файл для обрізання

Файл: `app/src/main/kotlin/com/uacastplayer/app/BackupController.kt`.
Функції: `encodePortableBackup`, `writeBackup`.

Capture перевіряв Job перед читанням FILE source та закривав input stream при
cancellation. Але після останнього файлу й під час синхронного JSON encoding
перевірки active Job не було. Якщо owner скасовувався в цей проміжок, код усе одно
відкривав output у `wt`. Cancellation callback реєструвався лише після відкриття;
навіть негайне закриття вже не відновлює обрізаний старий документ.

Контрольовані тести:

- передано вже cancelled Job для URL-only/empty-local backup;
- input stream останнього embedded FILE скасовує Job під час свого close,
  після завершеного читання, перед encoding/output.

До виправлення обидва випадки відкривали output **1 раз замість 0** та не
пропагували cancellation на цьому синхронному boundary. Тепер `ensureActive`
виконується перед capture, після нього, після encoding та перед відкриттям output.
Результат — **0 output opens** і CancellationException, а не FAILURE/SUCCESS.
Наявне закриття активних provider streams при cancellation збережено.

Це не обіцянка transactional rollback після вже розпочатого запису: SAF provider
може залишити частковий документ при помилці/скасуванні після відкриття. Цей етап
усуває відкриття при вже спостережуваному cancellation до destructive boundary.

## Тести

Перед production edits остаточний before-fix прогін: **5 tests, 5 failures,
0 errors/skips**. Результати збережено в ignored локальних файлах:

```text
app/build/backup-export-stabilization-20261003/before-concurrency.xml
app/build/backup-export-stabilization-20261003/before-cancellation.xml
```

Додано **10 тестів**: 3 file/concurrency, 2 cancellation/capture та 5 coordinator.
Покрито same-URI serialization, obsolete waiter, окремих owners, незалежні URI,
скасування waiter/owner, exception cleanup, відсутність idle registry entries
після 1 000 завершених synthetic destinations. Останнє не є heap benchmark.
Coordinator не створює detached scope, job, timeout чи додатковий worker pool.

Цільовий after-fix набір: **96 tests, 15 suites, 0 failures/errors/skips**,
app Detekt успішний. Він включає portable local playlists, corrupted payload,
size/storage/provider failures, import merge safety, startup reads, current-data
capture та backup UI. Assertions старих тестів не послаблювалися.

## Повний локальний gate

Запущено після останніх source/test змін:

```text
:app:assembleDebug :app:assembleDebugAndroidTest :app:detekt :core:test :core:detekt
:app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest
:app:lintDebug :app:lintRelease :app:lintPlay :app:assembleRelease
-Puacast.requireSigning=true --offline --max-workers=1 --no-daemon --continue
```

**BUILD SUCCESSFUL за 9m 9s**, 203 tasks: 43 executed, 160 up-to-date,
configuration cache reused. Після останніх source/test змін підтверджено:

| Перевірка | Результат |
| --- | --- |
| Debug unit/UI/screenshot | 2 540 tests, 425 suites, 0 failures/errors/skips |
| Release unit | 2 215 tests, 355 suites, 0 failures/errors/skips |
| Play unit | 2 215 tests, 355 suites, 0 failures/errors/skips |
| Core unit | 126 tests, 15 suites; unchanged/cache-valid, 0 failures/errors/skips |
| Roborazzi | 47 unchanged, 0 changed/added/recorded |
| App/core Detekt | Успішно; baseline/suppressions не розширено |
| Debug/Release/Play lint | 0 errors/warnings; по 1 наявному OldTargetApi Hint |
| Debug / AndroidTest / signed universal Release APK | Зібрані |
| `git diff --check` | Успішно; наявні CRLF notices не є source errors |

Кількості variants — не сума унікальних тестів: багато сценаріїв повторюються.
Нові backup-тести пройшли у всіх трьох variants. Screenshot goldens не редагувалися.
Нових Kotlin compiler warnings не отримано. Два наявні R8 async-parsing notices не
приховувалися suppressions чи incidental toolchain update.

15 `scripts/check-*.sh` пройшли перед gate та повторно після свіжої збірки.
Version і користувацькі дані не змінювалися.

## Локальний APK

`app/build/outputs/apk/release/app-universal-release.apk`: **0.9.7 / 164**, minSdk 24,
targetSdk 36, ABI `armeabi-v7a`, `arm64-v8a`, `x86_64`; 24 178 412 bytes;
створено о 17:21:11. Підпис v2/v3 підтверджено, один signer.

- APK SHA-256: `6BFD593C646D732F1BCCC080BD08AF0BA7E0B8EAF379DC27760F806DCC842407`.
- Certificate SHA-256: `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`.

Fingerprint замінює попередній локальний artifact з icon-cache стабілізацією.
VersionCode не піднято: це не новий discoverable update для встановленого code 164.

У цьому етапі не виконували GitHub-публікацію, встановлення на телефон/TV,
видалення сторонніх файлів чи вимкнення комп'ютера. Для native/cloud SAF поведінки
та end-to-end UI на телефоні потрібен окремий device QA; ці host тести не
оголошуються перевіркою Hisense VIDAA, Billing або Google Play publication.
