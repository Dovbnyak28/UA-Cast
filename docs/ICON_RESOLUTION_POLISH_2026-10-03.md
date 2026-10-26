# Полірування конкурентного отримання логотипів — 2026-10-03

## Межі

Перевірено шлях `ChannelIcon` / `IconPrefetcher` → `IconRepository` → IO/HTTP →
позитивний і негативний кеші. Також прочитано власників phone/TV remote та backup;
їх не переписано без окремого підтвердженого дефекту. Наявні сторонні зміни збережено.
Версію, залежності, дозволи, формат плейлистів, політику Lite/Premium та джерела
користувацьких паків не змінено. Це не сертифікація кожного рядка застосунку.

## P2 — дубльовані HTTP-запити та записи однієї іконки

Prefetch дедуплікував власну чергу, але не координувався з видимими рядками.
Кілька `resolveIconFile` читали порожній memory cache до завершення першого запиту
і кожен окремо починав candidate chain. Відтворення: 8 одночасних resolver-викликів
і керований локальний origin. Для 503 отримано **8 HTTP-запитів замість 1**.
Паралельний позитивний сценарій також падав: частина resolver-результатів була null.
Цей тест не ізолює конкретну файлову причину позитивних miss; окремий disk-cache
дефект лише за цим результатом не оголошується.

`IconResolutionCoordinator` тепер створює coroutine Mutex для пари ID/revision.
Після очікування репозиторій повторно читає memory cache, включно з cached miss.
Інший ID або нова revision не чекає старий запит. Немає detached scope, спільного
скасовуваного deferred-result, таймерів чи додаткових потоків. Завершені/скасовані
операції прибирають запис coordinator; idle IDs не накопичуються.

Після виправлення позитивний і негативний сценарії виконують **1 HTTP-запит на
8 resolver-викликів**. Усі позитивні результати non-null; негативні — null.
Це точний мережевий лічильник fixture, не вимірювання FPS або heap на телефоні.

## P2 — стара помилка заново блокує URL після успішного retry

Шлях: запит A вже в мережі → `retryTransientFailures` очищує failure store та
оновлює memory revision → новий B отримує 200 → A пізніше отримує 404/503 і записує
failure → згодом файл прибирається з disk cache → валідний URL знову вважається
заблокованим. Попередній generation guard захищав лише memory-cache publication,
не `failureStore.recordFailure`. Наявні TTL — година/тиждень залежно від помилки.

Revision тепер проходить через uncached/candidate/fetch шлях. Усі записи failure
виконуються одним `recordFailureIfCurrent` під тим самим cache lock. Clear/revision
у `retryTransientFailures` також атомарні відносно цих записів. Поточні справжні
помилки, їх TTL, cancellation propagation та fallback до наступного user pack
збережені. Ревізія cache maintenance консервативно відкидає й інші застарілі failure
результати: це не підміна їх новою позитивною відповіддю.

Два regression-тести утримують A до завершення B, потім моделюють звичайне пізніше
LRU-вилучення через видалення тестового файлу та invalidation memory cache. Повного
заповнення 256 MB кешу не виконано. До виправлення третій resolver повертав null;
після — знову отримує 200, загалом 3 HTTP-запити. Новий retry не чекає A.

## Перевірки до й після

Валідний before-fix прогін: **7 tests / 4 failures**, 3 cancellation/independence
контролі вже проходили. XML збережено локально в ignored
`app/build/icon-polish-20261003/before-fix-concurrency.xml`. Початковий fixture також
утримував повторний запит cancellation-тесту; цей хибний п'ятий failure усунуто
перед before-fix прогоном. Assertions production-дефектів не послаблено.

Додано **11 тестів**: 7 repository/HTTP і 4 coordinator. Перевіряються: успіх і miss,
скасування waiter/owner, відсутність негативного запису від cancellation, незалежний
канал/revision, 404/503 після retry, exceptions та cleanup записів. 1 000 послідовних
синтетичних IDs залишають 0 idle coordinator entries; це не memory benchmark.

Цільовий набір: **49 tests, 11 suites, 0 failures/errors/skips**, app Detekt — успішно.
Він включає попередні generation, removed-pack, source-policy, no-source fast-path,
prefetch progress/interruption та disk-cache регресії. Compiler opt-in для тестового
`runCurrent` явно оформлено; нових warnings або suppressions не залишено.

## Фінальний локальний gate

**BUILD SUCCESSFUL за 9m 12s**, 203 tasks (46 executed, 157 up-to-date), offline,
один worker, no daemon. Після останніх source/test змін виконано:

```text
:app:assembleDebug :app:assembleDebugAndroidTest :app:detekt :core:test :core:detekt
:app:verifyRoborazziDebug :app:testReleaseUnitTest :app:testPlayUnitTest
:app:lintDebug :app:lintRelease :app:lintPlay :app:assembleRelease
-Puacast.requireSigning=true --offline --max-workers=1 --no-daemon --continue
```

| Перевірка | Результат |
| --- | --- |
| Debug unit/UI + screenshots | 2 518 tests; 0 failures/errors/skips |
| Release unit | 2 193 tests; 0 failures/errors/skips |
| Play unit | 2 193 tests; 0 failures/errors/skips |
| Core unit | 126 tests; unchanged/cache-valid, 0 failures/errors/skips |
| Roborazzi | 47 unchanged; 0 changed/added/recorded |
| App/core Detekt | Успішно; baseline не розширено |
| Debug/Release/Play lint | 0 errors, 0 warnings; наявний OldTargetApi Hint |
| 15 `scripts/check-*.sh` | Усі пройшли до й після фінальної збірки |
| `git diff --check` | Успішно; наявні CRLF notices не є code errors |
| Debug/AndroidTest/signed universal Release APK | Зібрані; release signature v2/v3 підтверджено |

Кількості variants — повторні виконання багатьох тих самих випадків, не сума унікальних
тестів. Screenshot goldens не змінено. Нових Kotlin compiler warnings не залишено;
два наявні R8 async-parsing notices не приховано suppressions чи incidental SDK update.

Актуальний APK: `app/build/outputs/apk/release/app-universal-release.apk`.
Версія **0.9.7 / 164**, minSdk 24, targetSdk 36; ABI `armeabi-v7a`, `arm64-v8a`,
`x86_64`; 24 178 412 bytes.

- APK SHA-256: `A43586C044508EB2B6188215FECF1CBBEF7112E1D53369D7EAC1473AA5DF927F`.
- Certificate SHA-256: `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`.

Це локальна збірка, не нова GitHub-публікація. На телефон/TV у цьому етапі її не
встановлювали; host HTTP/coroutine тести не оголошуються native/device QA або
перевіркою повноцінного декодування PNG. Не виконували вимкнення комп'ютера чи
видалення користувацьких даних/сторонніх файлів.
