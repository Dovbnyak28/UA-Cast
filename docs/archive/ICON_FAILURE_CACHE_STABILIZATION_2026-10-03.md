# Стабілізація кешу помилок логотипів — 2026-10-03

## Межі роботи

Продовжено попереднє полірування icon resolution. Перевірено шлях:
`IconController.triggerPrefetch` → фонове `pruneExpiredFailures`, паралельно з
`ChannelIcon` / `IconPrefetcher` → `IconRepository` → `IconFailureStore`.
Наявні сторонні зміни збережено. UI, Lite/Premium, джерела користувацьких паків,
залежності, дозволи, versionName/versionCode та формати backup/playlist не змінено.
Це цільова стабілізація, а не твердження про відсутність усіх багів застосунку.

## Підтверджені дефекти

### P2 — очищення простроченого запису видаляє його свіже оновлення

Файл: `app/src/main/kotlin/com/uacastplayer/data/icons/IconFailureStore.kt`.
Функції: `prunePersistedFailures`, `shouldSkip`, `recordFailure`.

Фонове maintenance отримувало snapshot `prefs.all` з простроченим timestamp.
HTTP-запит міг після цього записати нову 404/403 для того самого URL. Maintenance
без перевірки поточного timestamp видаляло ключ зі свіжою помилкою. Аналогічно
`shouldSkip` читав старий timestamp і потім видаляв ключ, який уже міг оновитися.
`SharedPreferences` робить окремі операції thread-safe, але не всю таку послідовність.

Наслідок — втрата cooldown і непотрібні повторні HTTP-запити. Це не втрата
плейлистів/обраного і не доказ crash або release blocker.

Відтворення:

- Для prune wrapper над справжніми Robolectric SharedPreferences захоплює старий
  snapshot, записує свіжий timestamp і лише тоді повертає snapshot maintenance.
  Порядок операцій контрольований; це не випадковий probabilistic stress-test.
- Для lookup два реальні executor threads: читання timestamp утримується latch,
  інший потік намагається оновити запис, після чого lookup продовжується. До
  виправлення свіжа помилка зникає. Після виправлення перевірено й різних owners
  одного prefs file. Обмежене 500 ms очікування сигналу не є `Thread.sleep`.

Виправлення: permanent lookup/expiry removal і запис захищені одним process-local
metadata lock. Snapshot/filter maintenance лишається поза ним; перед batch removal
timestamp атомарно порівнюється з отриманим snapshot. Міграція використовує той
самий lock, щоб усі writers дотримувалися одного порядку. Lock не утримує Context
і не охоплює HTTP чи запис image bytes. Це не cross-process synchronization.

Для transient map removal тепер conditional `remove(key, recordedAt)` у lookup
та prune. Це захист того самого інваріанта; окремий примусово відтворений transient
race не оголошується третім підтвердженим багом.

### P2 — виправлення системного часу подовжує блокування URL

Файл: `app/src/main/kotlin/com/uacastplayer/icons/IconFailurePolicy.kt`.
Функція: `isExpired`.

Timestamp записувався за wall clock. Якщо годинник був помилково далеко вперед,
а згодом дата виправлялася назад, різниця `now - recordedAt` була від'ємною, і
помилка не спливала до майбутнього timestamp плюс TTL. Прострочені URL могли
залишатися заблокованими значно довше години/тижня. Зміна лише часового поясу
не є цим trigger: вона не змінює epoch milliseconds.

Timestamp з майбутнього тепер інвалідний і допускає нову спробу. Додатково
негативні timestamps відкидаються до subtraction: це hardening некоректних даних,
не окрема знайдена вразливість. Для валідних timestamps збережено обидва TTL і
точну межу: запис активний на TTL та спливає на TTL + 1 ms. Метод не скидає memory
cache самостійно; тест перевіряє failure policy/store при їх повторному зверненні.

## Тести до й після

Before-fix прогін: **20 tests, 6 failures, 0 errors/skips**. Два failures стосуються
renewal race, чотири — clock/invalid timestamp (включно з store integration).
Решта controls уже проходила. XML збережено в ignored локальних файлах:
`app/build/icon-failure-polish-20261003/before-store.xml` та `before-policy.xml`.

Додано **12 тестів**: 7 store/integration та 5 policy. Покрито:

- renew після maintenance snapshot; конкурентний lookup/write;
- видалення expired зі збереженням fresh, schema та сторонніх metadata keys;
- transient-only clear після network recovery та persisted records після recreation;
- clock correction, негативні та крайні Long timestamps, обидва TTL boundaries;
- збереження чинної поведінки одноразової міграції та future schema.

Перший after-fix цільовий набір: **70 tests, 14 suites, 0 failures/errors/skips**,
app Detekt успішний. Після нього додано два migration controls та узгоджено
migration lock; остаточна версія перевіряється повним gate нижче. Цільовий набір
охопив попередні resolution/cancellation/source-revision/prefetch регресії і
`IconMaintenanceSchedulingTest` — maintenance не перенесено на caller/Main.

## Остаточна перевірка

Повний локальний gate запущено після останніх source/test змін:

```text
:app:assembleDebug :app:assembleDebugAndroidTest :app:detekt :core:test :core:detekt
:app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest
:app:lintDebug :app:lintRelease :app:lintPlay :app:assembleRelease
-Puacast.requireSigning=true --offline --max-workers=1 --no-daemon --continue
```

**BUILD SUCCESSFUL за 9 хвилин**, 203 tasks: 48 executed, 155 up-to-date,
configuration cache reused. Після останніх змін підтверджено:

| Перевірка | Результат |
| --- | --- |
| Debug unit/UI/screenshot | 2 530 tests, 422 suites, 0 failures/errors/skips |
| Release unit | 2 205 tests, 352 suites, 0 failures/errors/skips |
| Play unit | 2 205 tests, 352 suites, 0 failures/errors/skips |
| Core unit | 126 tests, 15 suites; unchanged/cache-valid, 0 failures/errors/skips |
| Roborazzi | 47 unchanged, 0 changed/added/recorded |
| App/core Detekt | Успішно; baseline/suppressions не розширено |
| Debug/Release/Play lint | 0 errors, 0 warnings; по 1 наявному OldTargetApi Hint |
| Debug APK / AndroidTest APK / signed Release APK | Зібрані |
| `git diff --check` | Успішно; наявні CRLF notices не є code errors |

Кількості variants не слід додавати як унікальні тести: багато випадків повторюються
між ними. Обидва нові класи пройшли у всіх трьох variants. Screenshot goldens не
редагувалися. Два наявні R8 async-parsing notices не приховувалися; нових Kotlin
compiler warnings цей прогін не показав.

15 локальних `scripts/check-*.sh` пройшли перед gate та повторно після
свіжої збірки. Sandbox-only запуск Git Bash не знайшов `grep`,
тому перевірки повторено у дозволеному середовищі. Це не source failure і не
причина змінювати checker чи вимикати його.

APK: `app/build/outputs/apk/release/app-universal-release.apk`.
Версія **0.9.7 / 164**, minSdk 24, targetSdk 36, ABI `armeabi-v7a`, `arm64-v8a`,
`x86_64`; 24 178 412 bytes; локально зібрано о 16:48:17. Підпис v2/v3 перевірено,
один signer.

- APK SHA-256: `8ECD70241C561617FF6522DA1E8B981BF27BB3BFDDB735C85C43008ECD5B4D28`.
- Certificate SHA-256: `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`.

Цей fingerprint замінює попередній локальний artifact після icon-resolution
полірування. VersionCode не піднято, тому це не новий discoverable update для
пристрою з уже встановленим code 164.

Не виконували GitHub-публікації, встановлення на телефон/TV, видалення
користувацьких файлів чи вимкнення комп'ютера. Host тести не є заміною device QA,
real network battery/FPS benchmarks або підтвердженням сумісності Hisense VIDAA.
