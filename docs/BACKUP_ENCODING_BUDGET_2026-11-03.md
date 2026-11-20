# Обмеження пам'яті під час експорту backup — 03.11.2026

## Підтверджена проблема

**P2 — зайві тимчасові виділення пам'яті при відмові в завеликому backup.**
`BackupController.encodePortableBackup` будував повне дерево JSONObject/JSONArray,
потім весь JSON String і UTF-8 ByteArray, і лише тоді порівнював розмір з 8 MiB.
Ліміт захищав файл призначення, але не обсяг роботи та виділень до відмови.

`BackupExportMemoryTest` подає 4000 записів обраного з довгими URL. Дані fixture
створюються перед вимірюванням; класи прогріваються коротким експортом. Вимірюються
байти, виділені поточним потоком навколо синхронного `writeBackup`:

| Варіант | Виділено байтів | MiB | Результат |
| --- | ---: | ---: | --- |
| Початкове повне JSON-дерево | 144431960 | 137,7 | Тест бюджету 64 MiB не пройшов |
| Проміжний JsonWriter без BufferedWriter | 686921968 | 655,1 | Відхилено тестом, не опубліковано |
| Потоковий encoder із BufferedWriter | 17799952 | 17,0 | Бюджет пройдено |

Для цього fixture скорочення приблизно у **8,1 раза**. Це total allocations одного
потоку, а не peak heap, RSS або замір на фізичному телефоні з 128 MiB heap.
OOM не відтворювався і не подається як підтверджений production crash.
У фінальному повному Debug-прогоні отримано 17451616 байтів, у наступному
цільовому — 17452744 байти (16,6 MiB). Числа відрізняються через стан прогріву JVM;
усі фінальні прогони залишилися в початковому бюджеті 64 MiB.

Проміжна реалізація показала важливість тесту: JsonWriter також викликає
`Writer.write(int)`, і прямий UTF-8 writer коштував дорожче. Виклики перевірені
в bytecode того Android runtime, який виконує Robolectric. BufferedWriter збирає
символи перед UTF-8 encoder. Бюджет тесту не підвищували, помилкову реалізацію
збережено лише як лог/доказ у build directory.

## Зміна

Новий `data/backup/BackupDocumentEncoder` серіалізує по одному плоскому рядку
схеми. Використано платформний JsonWriter і стандартні BufferedWriter та
OutputStreamWriter. LimitedBackupOutput рахує фактичні UTF-8 байти, перевіряє Job
і зупиняє запис при перевищенні 8 MiB. Фізичний SAF output stream відкривається
лише після повного успішного кодування в межах бюджету.

Переліки полів не дублюються: encoder використовує ті самі schema factories
`BackupCodec.sourceToJson`, `favoriteToJson`, `settingsToJson`, які тепер internal.
Чистий BackupCodec не імпортує Android або data. Нових залежностей немає.

Збережено JSON version 2, усі поля джерел/обраного/налаштувань, embedded local
playlist bytes та encryption envelope. Враховуються root/settings objects,
спільні import object/property budgets. При досягненні byte limit обхід решти
обраного припиняється; часткові JSON-байти не передаються файловому провайдеру.

Власне coroutine cancellation продовжує виходити як CancellationException;
фатальні VM errors не приховуються. Потоковий encoder не створює detached jobs,
постійних buffers/cache або записів на Main.

## Перевірки сумісності

- Повне порівняння round-trip усіх schema fields, Unicode, emoji, лапок і control chars.
- Точна межа UTF-8 byte size: рівно limit приймається, на один байт менший limit — відмова.
- Лінивий список: oversize зупиняє обхід, object budget перевіряється до відвідування рядків.
- Already-cancelled Job і скасування посеред encoding не стають звичайною відмовою.
- Наявні provider failures, short overwrite/truncation, portable local M3U,
  encryption, latest-export ownership та restore tests.

Цільовий пакет **109 тестів пройшов без failures/skips**. Статичні зауваження
ReturnCount і довгий assert виправлено без suppressions або послаблення baseline.

## Фінальний gate

Перевірки завершені успішно:

| Набір | Обсяг | Тестів | Failures / errors / skips |
| --- | --- | ---: | --- |
| Debug / Roborazzi verify | Повний | 2834 | 0 / 0 / 0 |
| Debug | Фінальна backup-регресія | 109 | 0 / 0 / 0 |
| Release | Цільова backup-регресія | 104 | 0 / 0 / 0 |
| Play | Цільова backup-регресія | 104 | 0 / 0 / 0 |
| Android 16 | Native backup/crypto/UI/startup | 9 | 0 / 0 / 0 |

Після повного Debug-прогону збільшено лише Unicode-fixture у тесті, щоб валідний
JSON перевищував 16 KiB і проходив через кілька буферних блоків. Production-код
після повного прогону не змінювався. Розширений fixture перевірено у фінальних
цільових Debug/Release/Play прогонах. П'ять Compose-тестів виконуються лише в Debug,
де є потрібний test manifest. Release/Play у цьому етапі не є повним повторенням
старого 2371-тестового gate.

Detekt і архітектурні межі успішні; baseline не змінено. Debug/Play lint:
**0 errors, 0 warnings, 2 hints** кожен. Залишено інформаційні підказки про
target SDK/Compose BOM; їхні версії не змінювалися. APK і test APK зібрані.
Головний gate — **BUILD SUCCESSFUL** за 6 хв 17 с, фінальний цільовий —
**BUILD SUCCESSFUL** за 2 хв 9 с. Новий allocation-budget test входить у звичайний
unit suite; GitHub CI цього етапу ще не запускався.

На власному AVD `ua-cast-branding` / Android 16: **OK (9 tests)** за 16,265 с:

- BackupFileInstrumentedTest — 3: переносний M3U, відмова для пошкодженого backup,
  старий JSON та робота з реальними ContentResolver streams.
- SecurePortableBackupInstrumentedTest — 1: зашифрований backup і preview confirmation.
- BackupCipherInstrumentedTest — 1: реальний Android crypto provider і tampering detection.
- BackupSecureDialogInstrumentedTest — 3: password masking, confirmation і D-pad input.
- MainActivitySmokeTest — 1: запуск застосунку й наявність UI.

Прогін виконувався через `scripts/run-preserved-device-tests.ps1`. Початкові debug
files/preferences відновлено, **хеші 45 файлів перевірено**. Приватний recovery
archive не призначений для публікації. Crash buffer порожній. Після збору доказів
власний AVD зупинено; інший емулятор не змінювався.

У поточному Debug APK перевірено наявність нового encoder у DEX, loaded ELF
sections **15 native libraries**, **6 licence assets**, legal HTML і відсутність
вбудованих медіа/старого libmedia3ext. Додаткове читання старішого Play AAB
verifier-ом не означає, що новий Play AAB збирався в цьому етапі.

## Артефакт і докази

`build/backup-budget-2026-11-03/UA-Cast-backup-debug-universal.apk`:
applicationId `com.uacastplayer.debug`, версія 0.9.7 (164), **39 642 120 байтів**.
Це ізольований тестовий APK, не публічний application release.

SHA-256: `654622b4464dee27c7966c944fc5a4e7d18e239454b68bfeb7e2c96769fb7858`.

Докази в `build/backup-budget-2026-11-03`: `export-before.log/xml`,
`unbuffered-attempt.log/xml`, `export-after.xml`, `encoder-after.xml`,
`full-debug-memory.xml`, `full-debug-encoder.xml`, `full-validation.log`,
`final-targeted-validation.log`, `emulator-regression.log`, `preserved-emulator-run.log`,
`json-writer-bytecode.txt`, `final-verification.json`.

Фізичні телефони й телевізори не використовуються. Git index та сторонні
зміни збережено. Нового CI, комітів чи публічного application release немає;
історичний NextLib/GPL release blocker цим етапом не врегульовано.
