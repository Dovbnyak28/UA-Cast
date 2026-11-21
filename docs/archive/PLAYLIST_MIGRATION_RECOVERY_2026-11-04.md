# Міграція плейлистів: durable commit і повторна спроба з UI

Дата: 2026-11-04. Локальний етап стабілізації з fault injection файлових помилок.

## 1. Підтверджений дефект міграції — P2

Шлях: `PlaylistController.loadInitialSource` → `PlaylistSourceInitialization`
→ `PlaylistRepository.migrateLegacySnapshotIfNeeded` → `saveSources`.

Стара версія тримала весь плейлист у `playlist_snapshot.bin`. Міграція спочатку
копіює його до per-source snapshot, потім записує список джерел і видаляє legacy.
Якщо `saveSources` повертав false, legacy правильно залишався на диску, але
ініціалізація помилково повертала success і кешувала його у shared Deferred.

Наслідки підтверджені трьома тестами до виправлення (**3 tests, 3 failures**):

- Незбережене джерело публікувалося як успішно ініціалізоване; очікуваний
  StorageError не надходив.
- Після ремонту сховища той самий власник повертав кешований success замість
  повторного commit; на диску залишався порожній список джерел.
- Після успішного збереження видалення новий власник знову міг мігрувати
  залишений legacy-файл і повернути видалене джерело.

Fault injection блокує тільки `playlist_sources.bin.new` непорожнім тестовим
каталогом. Копіювання snapshot залишається працездатним. Це відтворення реальної
відмови файлового запису, а не вимірювання роботи пристрою з фізично заповненим диском.
Новий власник у тестах імітує повторний startup; процес Android не вбивається.

Виправлення: невдалий source-list commit стає IOException, яку вже обробляє
власник ініціалізації як StorageError. Не публікуються uncommitted sources,
failure не кешується як success, оригінал зберігається. Legacy видаляється тільки
після успішного commit. Формати файлів і порядок успішної міграції не змінені.

## 2. Recovery без перезапуску — функціональне покращення UX

Попереднє повідомлення вимагало перезапустити застосунок. Home і Channels
пропонували додати новий плейлист за відсутності sourceUrl; refresh без URL
нічого не робив. Backend retry існував, але звичайна дія UI його не запускала.

До зміни routing: PlaylistMigrationRetryTest — **2 tests, 2 failures**;
PlaylistStorageRecoveryUiTest — **3 tests, 2 failures**, контрольний сценарій
повторного вибору зовнішнього файла проходив. Докази: `retry-before.xml`,
`ui-before.xml`, `ui-before.log`.

- `PlaylistUiState.canRetryLoading` — обчислювана capability для чинного URL
  або помилки ініціалізації сховища; додаткового mutable UI state немає.
- Home і Channels показують чинну локалізовану дію retry для StorageError
  навіть без URL.
- `PlaylistController.refreshPlaylist` повторює ініціалізацію через спільний
  barrier. На час роботи показує loading, щоб не залишати активну error-кнопку.
- Звичайна помилка зовнішнього FILE без URL зберігає дію повторного вибору файла.
- Повідомлення StorageError оновлене чотирма мовами: звільнити місце й повторити
  спробу, без вимоги перезапуску.

## 3. Нестабільний тест пізнього дескриптора

Проміжний повний прогін: **2854 tests, 1 failure** у LocalVideoProxyTest.
Це збережено в `intermediate-full-failed.log` і `proxy-fixture-before.xml`.

`stop` спочатку скасовує provider open, потім `shutdownNow` перериває HTTP worker.
У fixture CountDownLatch.await міг кинути InterruptedException після release,
але до повернення дескриптора. Тоді дескриптора ще не було, хоча тест вимагав
одне закриття — він уже не моделював сценарій, для якого написаний.

Fixture тепер навмисно повертає пізній handle також після interrupt і зберігає
interrupt flag. Строгі assertions cancellation=1 і closed=1 та часові межі
не послаблені. Production-код проксі не змінювався; цей збій не рахується
підтвердженим витоком дескриптора або виправленням DLNA interoperability.

## Перевірки

Фінальний цільовий набір Playlist/Backup/LegacyMigration/LocalVideoProxy:
**312 tests, 0 failures/errors/skips**, BUILD SUCCESSFUL за 1 хв 34 с.
Detekt та архітектурні межі успішні, baseline не послаблено.

Остаточний повний gate: **BUILD SUCCESSFUL за 6 хв 20 с**.
Цільовий Release/Play gate: **BUILD SUCCESSFUL за 1 хв 54 с**.

| Набір | Обсяг | Тестів | Failures / errors / skips |
| --- | --- | ---: | --- |
| Debug | Повний, зі скриншотами | 2859 | 0 / 0 / 0 |
| Debug | Цільові Playlist/Backup/Legacy/Proxy | 312 | 0 / 0 / 0 |
| Release | Цільові Playlist/Backup/Legacy/Proxy | 284 | 0 / 0 / 0 |
| Play | Цільові Playlist/Backup/Legacy/Proxy | 284 | 0 / 0 / 0 |
| Android 16 | Native migration/recovery/backup/UI | 15 | 0 / 0 / 0 |

Нові unit/UI тести — 8: migration commit (3), refresh/recovery (2), UI routing
та контроль повторного вибору файла (3). Різниця 28 цільових Debug/Release тестів
пояснюється чинною Debug-only категорією RequiresComposeTestManifest:
BackupProgressUiTest (3), PlaylistSourceSafetyTest (7), PlaylistStorageRecoveryUiTest (3),
AddPlaylistScreenValidationTest (6), PlaylistSaveUiTest (3), BackupFileUiTest (3),
BackupSecureUiTest (3). Вони виконані в Debug; це не runtime skips.
Release/Play тут — цільова регресія, не повний повтор усіх suites або новий R8 build.

Debug і Play lint: **0 errors, 0 warnings, 2 hints** кожен. Detekt, архітектурні
межі та baseline успішні. Скріншоти не перезаписувалися. Попередні тести EPG
backup, скасування export і allocation budget великої копії залишаються зеленими.
Хеші змінених production/test файлів, baseline та legal readiness збігаються з
зафіксованими перед остаточним gate; production-код після нього не редагувався.

Власний AVD ua-cast-branding / Android 16: **OK (15 tests)** за 27,793 с,
без failures/assumptions:

- PlaylistMigrationCommitInstrumentedTest — 1: справжня відмова файлового commit,
  повторна міграція, durable removal і новий власник ініціалізації.
- PlaylistMigrationRecoveryUiInstrumentedTest — 1: MainActivity показує retry
  після StorageError, натискання кнопки відновлює legacy FILE без URL і без
  перезапуску Activity; тестове джерело потім видаляється штатним controller API.
- EmptyPlaylistInstrumentedTest — 1: порожній стан залишається доступним.
- BackupExportStopInstrumentedTest — 1; BackupEpgSelectionInstrumentedTest — 1;
  BackupFileInstrumentedTest — 3; SecurePortableBackupInstrumentedTest — 1;
  BackupCipherInstrumentedTest — 1; BackupSecureDialogInstrumentedTest — 4;
  MainActivitySmokeTest — 1.

Використано scripts/run-preserved-device-tests.ps1. Початкові debug-дані
відновлено, **хеші 45 файлів перевірено**. Приватний recovery archive не призначений
для публікації. Crash buffer порожній; власний AVD зупинено.

## Артефакт і докази

`build/migration-commit-2026-11-04/UA-Cast-migration-recovery-debug-universal.apk`:

- applicationId: com.uacastplayer.debug; версія 0.9.7 (164).
- Розмір **39 642 564 байти**.
- SHA-256: `a4379e7af13c8718276efa91cfbce6f88e79952fe5a4e0b50766ac827412c86e`.
- DEX містить commit guard, getCanRetryLoading і попередні виправлення backup.
- Loaded ELF sections 15 native libraries збігаються з локальною source-збіркою;
  6 licence assets і legal HTML відповідають вихідним файлам.
- Вбудованих плейлистів/відео та старого libmedia3ext немає.

Verifier додатково прочитав наявний старіший Play AAB; новий Play AAB у цьому
етапі не складався. APK призначений для локального тестування.

Докази у build/migration-commit-2026-11-04: before.log/xml, retry-before.xml,
ui-before.log/xml, intermediate-full-failed.log, proxy-fixture-before.xml,
targeted.log, targeted-results.json, targeted-*.xml, full-validation.log,
full-test-results.json, full-*.xml, release-play-validation.log,
emulator-regression.log, preserved-emulator-run.log, emulator-crash-buffer.log,
architecture.log, baseline.log, validated-source-hashes.json,
packaging-verification.json, final-verification.json і verify-results.py.

Git index і сторонні зміни збережені. Нових комітів, GitHub CI або публічного
application release цього етапу немає.

Нових залежностей, scopes, workers, assets або анімацій немає. Фізичні телефони,
Xiaomi TV та реальні Cast/DLNA receivers цього етапу не використовуються.
Історичне питання NextLib/GPL і вимоги до публікації залишаються окремими;
approval flags і commerce configuration не змінені.
