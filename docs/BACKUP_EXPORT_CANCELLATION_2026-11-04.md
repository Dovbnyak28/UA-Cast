# Скасування резервного копіювання без блокування інтерфейсу

Дата: 2026-11-04. Локальний етап стабілізації після виправлення EPG у backup.

## Підтверджена проблема

P2 — неможливо вийти з модального прогресу експорту, поки повільний файловий
провайдер не завершить операцію. Це не підтверджений ANR або crash.

Шлях: `rememberBackupDocumentActions` → `BackupController.exportCurrentTo`
→ SAF output stream → `exportInProgress=true` → `BackupProgressDialog`.
Діалог отримував порожній onDismiss і не містив кнопки скасування; controller
також не надавав дії для скасування лише поточного експорту.

До зміни production-коду додано Compose-регресію через справжній
`rememberBackupDocumentActions` та AppViewModel. Контрольований output stream
починає запис і чекає на close. Тест дійшов до активного провайдера й упав
на відсутньому вузлі «Скасувати»: **1 тест, 1 failure** (`before.log/xml`).
Це симуляція завислого провайдера, не доказ збою конкретного Google Drive/OEM.

Початковий `setup-timeout.log/xml` містить проблему fixture з прокачуванням
Robolectric Main looper. Він збережений окремо і не рахується як дефект застосунку.

## Виправлення

- Controller відстежує Job останнього експорту; `cancelExport` прибирає
  модальний стан, скасовує саме цей job і не публікує успішне збереження.
- Скасований job зберігає відповідальність за close потоку, очищення власної
  копії пароля та звільнення блокування документа. Cleanup не переноситься в UI.
- Пізнє завершення старого job не від'єднує новий job і не ховає його прогрес.
  Завершений успішний експорт зберігає повідомлення про успіх.
- `BackupProgressDialog` має явну nullable-дію onCancel. Експорт і читання
  отримали кнопку «Скасувати» з чинних перекладів та підтримкою TV/D-pad.
- Підтверджене застосування restore залишається без скасування: цей етап
  змінює кілька сховищ, і переривання посередині могло б залишити частковий стан.
- Натискання поза вікном прогресу не перериває запис випадково; кнопка й Back
  скасовують тільки операції, яким передано onCancel.

Нових detached scopes, workers, залежностей, растрових assets чи анімацій немає.
Правила Premium/комерції та формат backup не змінювалися.

## Реальні межі скасування

Generic SAF не забезпечує атомарної заміни файла. Якщо output уже відкритий,
скасування може залишити неповний файл; наявний файл неможливо гарантовано
відкотити. UA-Cast не видаляє документ користувача й не називає цей результат
успішною копією. Повторний завершений експорт записує файл з режимом `wt`.

Скасування корутини не може примусово зупинити довільний provider RPC, який ще
не повернув stream. UI можна закрити; скасований job завершує cleanup після
повернення провайдера. Примусового вбивства потоків або стороннього процесу немає.

## Перевірки

Цільовий Debug backup-набір: **126 тестів, 0 failures/errors/skips**.
Detekt і архітектурні межі успішні; baseline не послаблено.

Нові unit/UI перевірки (6):

- Скасування очікування startup не викликає dataSupplier і не відкриває файл.
- Скасування заблокованого запису закриває stream і дозволяє повторний експорт
  у той самий документ; після завершеного retry результат залишається SUCCESS.
- Пізнє завершення старої операції не впливає на прогрес/скасування нової.
- Скасування експорту через справжній UI wiring приховує діалог і закриває stream.
- Читання має явну кнопку скасування.
- Підтверджене застосування відновлення не пропонує переривання.

Повний Debug/Roborazzi прогін: **2851 тест, 0 failures/errors/skips**.
Повний gate із Debug/Play lint, detekt, APK і test APK завершився
**BUILD SUCCESSFUL за 6 хв 25 с**. Попередні EPG restore та export memory-budget
регресії залишаються зеленими. У memory fixture — 17 374 368 байтів сумарних
алокацій поточного JVM-потоку для 4000 довгих записів за бюджету 64 MiB;
це не Android peak heap і не новий вимір прискорення.

Власний Android 16 AVD: **OK (12 tests)** за 22,273 с, без failures/assumptions:

- BackupExportStopInstrumentedTest — 1: скасування очікування, відсутність
  output-файла й успішний наступний експорт через реальний ContentResolver.
- BackupEpgSelectionInstrumentedTest — 1: явне вимкнення EPG зберігається.
- BackupFileInstrumentedTest — 3: переносний M3U, старий JSON, пошкоджені файли.
- SecurePortableBackupInstrumentedTest — 1: пароль, preview і локальний M3U.
- BackupCipherInstrumentedTest — 1: Android crypto provider і tampering detection.
- BackupSecureDialogInstrumentedTest — 4: маскування пароля, підтвердження,
  вихід із password-полів та нова дія скасування прогресу через D-pad.
- MainActivitySmokeTest — 1: запуск застосунку.

Прогін виконав `scripts/run-preserved-device-tests.ps1`. Початкові debug-дані
відновлено; **хеші 45 файлів перевірено**. Приватний recovery archive не призначений
для публікації. Crash buffer порожній. Власний AVD зупинено.

Цільовий Release/Play backup-прогін: **BUILD SUCCESSFUL за 1 хв 12 с**.

| Набір | Обсяг | Тестів | Failures / errors / skips |
| --- | --- | ---: | --- |
| Debug | Повний, зі скриншотами | 2851 | 0 / 0 / 0 |
| Debug | Цільові Backup | 126 | 0 / 0 / 0 |
| Release | Цільові Backup | 117 | 0 / 0 / 0 |
| Play | Цільові Backup | 117 | 0 / 0 / 0 |
| Android 16 | Native backup/crypto/UI/startup | 12 | 0 / 0 / 0 |

Різниця 9 тестів у цільових variants — Debug-only категорія
`RequiresComposeTestManifest`: BackupFileUiTest (3), BackupSecureUiTest (3),
BackupProgressUiTest (3). Вони виконані в Debug. Release/Play тут перевіряють
backup-регресію; це не повний повтор усіх release-like unit suites і не R8 build.

Debug/Play lint: **0 errors, 0 warnings, 2 hints** кожен. Нових compiler
warnings/errors у логах gate немає. Хеші змінених production/test файлів та
baseline/legal readiness збігаються із зафіксованими перед повним gate.
Production-код після нього не редагувався.

## Артефакт і докази

`build/backup-cancellation-2026-11-04/UA-Cast-cancellable-backup-debug-universal.apk`:

- applicationId: `com.uacastplayer.debug`.
- Версія 0.9.7 (164), **39 642 704 байти**.
- SHA-256: `e140156aa27e5251fadd52f04807e7e860327fa8f40c6b53ebe05738f280a6fe`.
- DEX містить новий `cancelExport` та попереднє виправлення BackupEpgPolicy.
- Loaded ELF sections 15 native libraries збігаються з локальною source-збіркою;
  6 licence assets і legal HTML відповідають вихідним файлам.
- Вбудованих плейлистів/відео та старого libmedia3ext немає.

Verifier додатково прочитав старіший наявний Play AAB; новий Play AAB цього етапу
не складався. Артефакт призначено для локального тестування.

Докази у `build/backup-cancellation-2026-11-04`: `before.log/xml`,
`setup-timeout.log/xml`, `targeted.log`, `targeted-results.json`, `targeted-*.xml`,
`full-validation.log`, `full-test-results.json`, `full-*.xml`,
`release-play-validation.log`, `emulator-regression.log`, `preserved-emulator-run.log`,
`emulator-crash-buffer.log`, `architecture.log`, `baseline.log`,
`validated-source-hashes.json`, `packaging-verification.json`,
`final-verification.json` та `verify-results.py`.

Git index і сторонні зміни збережені. Нових комітів, GitHub CI чи публічного
application release цього етапу немає.

Фізичні телефони, Xiaomi TV та реальні Cast/DLNA receivers у цьому етапі
не використовуються. Історичне питання NextLib/GPL і вимоги до публікації
залишаються окремими відкритими питаннями; їхні approval flags не змінені.
