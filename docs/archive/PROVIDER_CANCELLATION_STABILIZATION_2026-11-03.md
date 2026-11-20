# Скасування доступу до файлових провайдерів — 03.11.2026

## Обсяг та докази

Простежено шляхи відкриття локального відео, читання назви M3U, локального
відео через proxy та сусідні сценарії backup. Зміни не додають можливостей,
залежностей, потоків, detached jobs або нового відтворювача.

У контрольованих тестах до production-fix: **16 тестів, 6 failures, 10 controls pass**.
`metadata-before.xml` і `proxy-before.xml` збережено окремо від наступних результатів.

1. **P2 — зайва робота після скасування.** `AndroidLocalVideoReader.readMetadata`
   після запізнілого повернення cursor виконував `getType`, хоча його сигнал
   уже був cancelled. Тест зупиняв query, скасовував Job і повертав cursor:
   лічильник MIME-запитів дорівнював 1 замість 0. Cursor закривається в обох випадках.
   Тепер cancelled signal перевіряється перед MIME-запитом і після нього,
   перш ніж відкривати descriptor. Це також зупиняє непотрібні наступні звернення
   при пізньому завершенні `getType`.

2. **P2 — захист cancellation callback.** У `AndroidLocalVideoReader.read` і
   `PlaylistFileLoader.documentName` прямий `CancellationSignal.cancel()` дозволяв
   винятку callback стати `CompletionHandlerException` в exception handler корутини.
   Це відтворено in-process тестовим ContentProvider для IllegalStateException
   та CancellationException. Самі тестові Jobs залишалися cancelled.
   Вихід такого callback за boundary небезпечний для власника без exception handler.

3. **P2 — захист закриття proxy descriptor.** Тестовий `LocalVideoSource`, callback
   якого кидає CancellationException, переривав `LocalVideoRead.close` до
   `handle.close()`. До виправлення semaphore закриття descriptor не спрацьовував.
   Наявний контроль з IllegalStateException вже проходив: runCatchingNonFatal
   ловив його, але навмисно пропускав CancellationException.

Пункти 2–3 — перевірка захисної межі контрольованим несправним провайдером,
не підтвердження crash на конкретному OEM/Google Drive або телевізорі.
Robolectric in-process provider і synthetic source не доводять однакової
поведінки всіх зовнішніх Binder-провайдерів; до release blockers ці сценарії
не підвищувалися. Частота таких відмов на реальних пристроях не вимірювалася.

## Реалізація

`data/ProviderCancellation.kt` містить єдину операцію `cancelProviderSafely`.
Вона ловить Exception лише під час завершення provider access. Це включає
CancellationException, кинутий стороннім callback, коли запит уже завершується.
Власний Job залишається cancelled; coroutine body продовжує поширювати своє
скасування за попередніми правилами. Fatal VM errors не приховуються.

Використання обмежено трьома місцями: metadata video, metadata playlist та
закриття local-video proxy read. Лог містить лише клас винятку, без URI,
назви документа, пароля або тексту provider exception.
Єдиний новий `TooGenericExceptionCaught` suppression обмежено цією короткою
boundary-функцією; detekt baseline не змінювався.

Після виправлення цільовий пакет **126 тестів пройшов без failures/errors/skips**.
Він включає normal metadata/cursor/descriptor closure, досягнення cancellation
провайдера, відсутність пізнього MIME-запиту, обидва типи callback exception,
HTTP HEAD/GET/range/token/stop, latest-selection ownership і backup сценарії.

Перший статичний gate знайшов два довгі повідомлення assert у новому тесті.
Їх скорочено без зміни перевірок або production-логіки; правила не послаблювалися.

## Фінальна перевірка

Усі заплановані перевірки завершені успішно:

| Набір | Обсяг | Тестів | Failures / errors / skips |
| --- | --- | ---: | --- |
| Debug / Roborazzi verify | Повний | 2827 | 0 / 0 / 0 |
| Release | Цільова provider/backup регресія | 126 | 0 / 0 / 0 |
| Play | Цільова provider/backup регресія | 126 | 0 / 0 / 0 |
| Android 16 | Native video, proxy, backup, startup | 13 | 0 / 0 / 0 |

Release/Play у цьому етапі перевірялися цільовими наборами, а не повним
2371-тестовим прогоном попереднього етапу. Попередні зелені результати не
подаються як повторний повний gate нового коду.

Detekt, архітектурні межі та порожній detekt baseline успішні. Debug/Play lint:
**0 errors, 0 warnings, 2 hints** кожен. Підказки стосуються target SDK/Compose BOM;
версії залежностей і target SDK не змінювалися. Debug APK і test APK зібрані.
Головний gate — **BUILD SUCCESSFUL** за 7 хв 21 с; цільовий Release/Play —
**BUILD SUCCESSFUL** за 1 хв 26 с.

На власному AVD `ua-cast-branding` / Android 16 native-прогін завершився
**OK (13 tests)** за 34,619 с, без assumption-пропусків:

- `LocalVideoInstrumentedTest` — 8 тестів: реальний MP4, seek/replay,
  ContentResolver descriptor, HTTP ranges та Cast/DLNA metadata.
- `BackupFileInstrumentedTest` — 3: переносний локальний M3U, пошкоджений
  backup без мутації даних і сумісність зі старою версією JSON.
- `SecurePortableBackupInstrumentedTest` — 1: зашифрований файл і явне
  підтвердження preview перед відновленням.
- `MainActivitySmokeTest` — 1: запуск і наявність UI.

Це не перевірка відтворення на фізичному Cast/DLNA receiver. Прикладні дані
ізольовано `scripts/run-preserved-device-tests.ps1`; початкові debug files/preferences
відновлено, **хеші 45 початкових файлів перевірено**. Recovery archive і fixture
data приватні та не призначені для публікації. Crash buffer порожній.
Після збору доказів власний AVD зупинено; інший емулятор не змінювався.

У поточному Debug APK звірено loaded ELF sections **15 native libraries**,
**6 licence assets** і legal HTML з локальними перевіреними джерелами.
Немає вбудованого медіа або старого `libmedia3ext`. Допоміжний verifier також
звірив старіший локальний Play AAB; новий Play AAB у цьому етапі не збирався.

## Артефакт

`build/provider-stabilization-2026-11-03/UA-Cast-provider-debug-universal.apk`:
applicationId `com.uacastplayer.debug`, версія 0.9.7 (164), **39 642 120 байтів**.
Це ізольована тестова debug-збірка, не публічний release APK.

SHA-256: `6b8dc51f72567dc9bd00901d8a01c518a8af312f3d420699211cafb23dd1e270`.

Докази в `build/provider-stabilization-2026-11-03`: `metadata-before.log`,
`metadata-before.xml`, `proxy-before.xml`, `metadata-after.xml`, `proxy-after.xml`,
`full-validation.log`, `release-play-targeted.log`, `emulator-regression.log`,
`preserved-emulator-run.log`, `emulator-crash.log`, `final-verification.json`.

Фізичні телефони й телевізори не використовуються. Нового CI, комітів,
публічного release та змін до legal readiness gate в цьому етапі немає.
