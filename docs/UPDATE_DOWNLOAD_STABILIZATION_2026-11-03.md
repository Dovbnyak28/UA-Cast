# Стабілізація завантаження оновлень — 03.11.2026

## Підтверджені збої

Перевірено шлях `AppViewModel → UpdateInstallController → UpdateDownloader →
executeCancellable → .part → verified APK`. Зміни цього етапу обмежені завантажувачем
і регресійними тестами; іконки, покупки та встановлення пакунка не перероблялися.

**P2: конфлікт кешу між власниками.** Кожен `AppViewModel` створює свій
`UpdateDownloader`. Екземпляри мали окремі mutex, хоча адресували однакові файли
`SHA256(url).apk.part` та `SHA256(url).apk` у спільному `cacheDir/updates`.
Скасування старого власника не означає, що його блокуюче IO вже дійшло до `finally`.
Новий власник міг почати запис до завершення попереднього; старий запис/rename/cleanup
зачіпав новий файл. Контрольований тест двох власників до виправлення отримав
`Replacement must complete, got Failed`.

**P2: публікація після скасування останнього блоку.** OkHttp Call скасовувався, але
вже буферизований body міг завершитися без IOException. `copyHashing` і `publish`
не перевіряли Job; перевірка в `executeCancellable` виконувалася вже після rename.
Тест зупиняв операцію після запису останніх 32 байтів, скасовував Job і відпускав IO.
До виправлення залишався APK: `A cancelled buffered response must not publish an APK`.

Це підтвердження можливості збою при заданому порядку подій, не вимірювання його
частоти на реальних пристроях. Втрата плейлистів або обхід signature gate не виявлені;
зачеплено тимчасовий файл оновлення та можливість завершити завантаження.

## Виправлення

- Mutex належить спільному кешу протягом життя процесу, а не одному ViewModel.
  Наступне завантаження чекає, доки попереднє IO закриє stream і прибере partial.
  Очікування cancellable, на Main блокування немає; Context у mutex не зберігається.
- Job перевіряється на кожному кроці копіювання та перед публікацією APK.
  Скасування виходить як CancellationException, `finally` прибирає partial.
- Залишено ліміти розміру, SHA-256, повторне використання перевіреного кешу та
  наявний signature/package gate. Нових залежностей або detached jobs немає.

Одна спільна lane навмисно серіалізує завантаження оновлень у процесі, зокрема для
різних URL. UI вже дозволяє лише одну активну установку; паралельність цього кешу
не дає користувацької переваги, а записи/cleanup мають спільного власника.

## Регресійні перевірки

- `UpdateDownloadCancellationTest.cancellationAfterTheLastChunkCannotPublishAnApk`
  — після скасування не залишається APK або partial.
- `aRetiringOwnerCannotDeleteAnotherOwnersReplacement` — старий скасований власник
  не пошкоджує нове завантаження, його bytes і digest залишаються правильними.
- Наявний `aCancelledReaderCannotInterfereWithTheNextDownloadOnTheSameOwner`
  зберігає захист при повторному використанні того самого екземпляра.
- `UpdateDownloaderTest.independent owners reuse the same verified download`
  перевіряє, що два власники дістають правильні bytes через один HTTP download.
- Суміжні controller/install, corrupt/oversize/network-failure та cache-reuse tests.

До production-fix: **3 тести, 2 failures**. Після fix перший цільовий пакет:
**63 тести, 0 failures/errors/skips**. Після цього додано контроль повторного
використання кешу двома власниками; він входить у фінальний прогін.

Докази: `build/stabilization-2026-11-03/update-before.log`, `update-before.xml`,
`update-after.log`, `update-after.xml`, `targeted-results.json`.

## Фінальна перевірка

Повні application unit-прогони завершені:

| Набір | Тестів | Failures / errors / skips |
| --- | ---: | --- |
| Debug із Roborazzi verify | 2820 | 0 / 0 / 0 |
| Release | 2371 | 0 / 0 / 0 |
| Play | 2371 | 0 / 0 / 0 |
| Core, без змін, Gradle up-to-date | 133 | 0 / 0 / 0 |
| Decoder unit, без змін, Gradle up-to-date | 5 | 0 / 0 / 0 |

Це кількості для окремих варіантів: одна й та сама логіка повторно перевіряється
у Debug/Release/Play; їх сума не є кількістю унікальних сценаріїв. Compose-тести
виконуються в Debug, де присутній їхній test manifest.

Перший загальний gate після успішних unit-прогонів зупинився на одному
`MaxLineLength` у новому виклику `copyHashing`. Виклик рознесено на кілька рядків
без зміни логіки, suppressions/baseline не розширено. Повторний detekt, Debug/Play
lint та складання APK/test APK завершилися **BUILD SUCCESSFUL** (3 хв 37 с).
Після цього виконано native-прогін із новим APK. Повні unit-прогони після зміни
лише форматування не повторювалися; початковий зупинений gate збережено в логах.

Debug і Play lint: **0 errors, 0 warnings, 2 hints** кожен. Залишені інформаційні
підказки про target SDK/Compose BOM; залежності й target SDK в цьому виправленні
не оновлювалися. Нових build warnings не зафіксовано.

Архітектурні межі, premium purity та порожній detekt baseline успішно перевірені.
Play guard перевірив поточні merged manifest/BuildConfig: немає restricted install
permissions і receiver, `SELF_UPDATER_ENABLED=false`. Це перевірка конфігурації,
не новий аналіз DEX чи нова публікація Play AAB.

На власному ізольованому Android 16 AVD `ua-cast-branding`:
**OK (9 tests)**, 4,47 с, без failures або assumption-пропусків.
Виконані 8 `UpdateInstallChainInstrumentedTest` і `MainActivitySmokeTest`:
реальні PackageManager/signing/archive APIs, неправильний пакет, HTML/обрізаний
APK, SHA-256/size/cache reuse, oversized/404 response та запуск MainActivity.
Нові cancellation/ownership regression-сценарії відтворені в host-тестах; ці 9
native-перевірок не є повторенням усієї попередньої 166-тестової UI-регресії.
Crash buffer після прогону порожній; власний AVD після збору доказів зупинено.

У поточному universal Debug APK звірено loaded ELF sections усіх **15 native
libraries**, **6 licence assets** і legal HTML з локальними перевіреними оригіналами.
Немає вбудованих медіафайлів і старого `libmedia3ext`.
Додаткова перевірка старішого локального Play AAB не означає, що в цьому етапі
збирався новий Play AAB; актуальним артефактом цього етапу є наведений нижче APK.

Результати у `build/stabilization-2026-11-03`: `unit-results.json`,
`full-validation.log`, `remaining-validation.log`, `emulator-update-regression.log`,
`emulator-crash.log`, `debug-artifact-verification.json`, `final-verification.json`.

## Поточний APK

`build/stabilization-2026-11-03/UA-Cast-stabilization-debug-universal.apk`:
applicationId `com.uacastplayer.debug`, версія 0.9.7 (164), **39 642 120 байтів**.

SHA-256: `b9fd5f5aeef7789ecd65c26197f4f0742869499aaebab33e60b2f64c8feccf97`.

Це ізольована тестова debug-збірка, не публічний release APK.

Фізичні телефони й телевізори в цьому етапі не використовуються. Git index і
сторонні зміни збережено. Коміти, GitHub CI та публічний application release
цього етапу не створювалися. Історичний NextLib/GPL legal blocker залишається чинним.
