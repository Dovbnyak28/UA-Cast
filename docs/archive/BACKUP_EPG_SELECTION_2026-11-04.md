# Збереження явного вимкнення телегіда в резервній копії

Дата завершення: 2026-11-04. Локальна стабілізація Android-застосунку.

## Підтверджений дефект

P2 — втрата користувацького вибору під час перенесення налаштувань.
Відтворено через публічні `AppViewModel.exportBackupTo` / `importBackupFrom`
з новими ViewModelStore для вихідного та цільового пристрою:

1. На вихідному пристрої користувач явно вимикає телегід:
   `customEpgUrl=null`, `hasChosenEpgSource=true`.
2. Експортує копію.
3. На цільовому пристрої вже налаштована інша адреса EPG.
4. Після відновлення цільова адреса залишалася активною, хоча копія мала
   перенести явне вимкнення.

Причина: export представляв і «не обирав джерело», і «явно вимкнув» одним
`epgCustomUrl=null`; import пропускав null. Відновити ці два різні наміри
з попереднього формату неможливо.

До виправлення: **3 тести, 1 failure**. Контрольні сценарії без вибору EPG
та старий JSON без поля налаштування проходили. Докази збережено в
`build/backup-epg-2026-11-03/before.log` і `before.xml`.

## Виправлення та межі сумісності

- Додано необов'язкове `settings.epgDisabled: true`. False не записується.
  Відсутність поля в старих копіях залишає чинне джерело цільового пристрою.
- Приймається тільки JSON boolean true: рядок `"true"`, числа, null, об'єкти
  та масиви не повинні вимикати телегід.
- `BackupEpgPolicy` визначає однаковий намір для preview та застосування:
  null — зберегти поточне, порожній рядок — вимкнути, валідна нормалізована
  HTTP(S)-адреса — налаштувати користувацьке джерело.
- Якщо вручну редагований файл містить і true, і адресу, явне вимкнення
  має пріоритет; preview обіцяє ту саму дію, яку виконає restore.
- Import використовує чинний setter EPG: скасовує завантаження, очищає
  старі дані, зберігає вибір і захищає його від автоматичного ввімкнення
  адресою з плейлиста. Таку адресу можна запропонувати користувачеві.
- Preview показує «Вимкнути телегід» українською та відповідний переклад
  англійською, іспанською, російською. Адреси та облікові дані не показуються.
- Нормалізовані еквівалентні адреси не рахуються як зміна налаштування.

Версія JSON залишається 2: додано необов'язкове поле, старі v1/v2 читаються.
Старий reader формату v2 проігнорує невідоме поле; це **не** виправляє втрату
вибору «вимкнено» в старому застосунку. Reader, який підтримує лише v1, як і
раніше не зможе читати v2. Для нової ознаки потрібне оновлення reader.
Вбудовані EPG-каталоги та старі preset IDs не відновлюються.

## Регресія

Цільовий Debug-прогін Backup/EPG: **262 тести, 0 failures/errors/skips**.
Повний Debug-прогін із Roborazzi: **2845 тестів, 0 failures/errors/skips**.
Нові 11 unit/UI тестів включають 3 публічні export/import сценарії,
7 перевірок формату/preview policy та 1 перевірку тексту й підтвердження preview.
Розширено наявні encoder і native encrypted-backup fixtures новим boolean-полем.

Тест великого export і його allocation budget залишився зеленим:
17 451 584 байти сумарних алокацій поточного JVM-потоку для fixture з 4000
довгих обраних записів, бюджет 64 MiB. Це не peak heap на Android-пристрої.

Core (133) і decoder unit (5) включені в gate; Gradle визначив їх як UP-TO-DATE,
бо відповідні входи не змінені. Їх не слід рахувати як нове фізичне виконання.

Повний gate: **BUILD SUCCESSFUL за 7 хв 37 с**. Debug/Play lint, detekt,
Debug APK і androidTest APK зібрані успішно; архітектурні межі та baseline
перевірені без послаблення правил.

На власному Android 16 AVD `ua-cast-branding`: **OK (10 tests)** за 16,333 с,
без failures або assumptions:

- BackupEpgSelectionInstrumentedTest — 1: export/import через AppViewModel,
  новий власник, вимкнення старого EPG і захист від автоувімкнення плейлистом.
- BackupFileInstrumentedTest — 3: реальні ContentResolver streams, переносний
  локальний M3U, старий JSON та відмова для пошкодженого файлу.
- SecurePortableBackupInstrumentedTest — 1: пароль, локальний M3U і нове
  boolean-поле зберігаються; до підтвердження дані не застосовуються.
- BackupCipherInstrumentedTest — 1: Android crypto provider і tampering detection.
- BackupSecureDialogInstrumentedTest — 3: маскування пароля, D-pad і новий
  текст вимкнення EPG у preview.
- MainActivitySmokeTest — 1: запуск і наявність екрана застосунку.

Використано `scripts/run-preserved-device-tests.ps1`. Початкові debug-дані
відновлено; хеші **45 файлів** перевірено. Recovery archive приватний,
не призначений для публікації. Crash buffer після прогону порожній.
Власний AVD зупинено; фізичні пристрої не використовувалися.

Цільовий Release/Play gate: **BUILD SUCCESSFUL за 2 хв 24 с**.

| Набір | Обсяг | Тестів | Failures / errors / skips |
| --- | --- | ---: | --- |
| Debug | Повний, зі скриншотами | 2845 | 0 / 0 / 0 |
| Debug | Цільові Backup/EPG | 262 | 0 / 0 / 0 |
| Release | Цільові Backup/EPG | 243 | 0 / 0 / 0 |
| Play | Цільові Backup/EPG | 243 | 0 / 0 / 0 |
| Android 16 | Native backup/crypto/preview/startup | 10 | 0 / 0 / 0 |

Різниця 19 тестів між цільовими Debug і Release/Play — чинна категорія
`RequiresComposeTestManifest`: EpgGuideTimeZoneTest (6), BackupFileUiTest (3),
BackupSecureUiTest (3), EpgSettingsSectionTest (7). Вони виконані в Debug;
це не пропуски всередині Release/Play прогону. Release/Play тут не є повним
повторенням усіх тестів цих variants.

Debug і Play lint: **0 errors, 0 warnings, 2 hints** кожен. Нових compiler
warnings або errors у логах цього gate немає. Baseline та legal readiness
конфігурація не змінені. Хеші production-файлів, зафіксовані для повного gate,
збігаються після завершення всіх перевірок; production-код після нього не редагувався.

## Артефакт і докази

Тестовий universal APK:
`build/backup-epg-2026-11-03/UA-Cast-epg-backup-debug-universal.apk`.

- applicationId: `com.uacastplayer.debug`.
- Версія: 0.9.7 (164), **39 642 704 байти**.
- SHA-256: `b5e757651c28ad5735cea356809a7b1723e11d4fb524309812b16fc678a36463`.
- У DEX перевірено нові BackupEpgPolicy та epgDisabled.
- Loaded ELF sections 15 native libraries звірено з локальною source-збіркою;
  6 licence assets і legal HTML відповідають вихідним файлам.
- Вбудованих плейлистів, відео та старого libmedia3ext у APK немає.

Verifier додатково прочитав наявний старіший Play AAB; новий Play AAB у цьому
етапі не складався. Цей APK — debug-артефакт для перевірки.

Докази у `build/backup-epg-2026-11-03` (виправлення почате 03.11, завершене 04.11):
`before.log/xml`, `targeted.log`, `targeted-results.json`, `targeted-*.xml`,
`full-validation.log`, `full-test-results.json`, `full-*.xml`,
`release-play-validation.log`, `emulator-regression.log`, `preserved-emulator-run.log`,
`emulator-crash-buffer.log`, `packaging-verification.json`,
`validated-source-hashes.json`, `final-verification.json` та `verify-results.py`.

Git index і сторонні зміни збережені. Нових комітів, GitHub CI чи публічного
application release у цьому етапі немає.

Цей етап не перевіряє фізичний DLNA/Chromecast receiver, Google Play checkout
або телевізор Xiaomi. Історичні NextLib/GPL release blocker і вимоги до
видавця залишаються окремими відкритими питаннями; їхні approval flags не змінені.
