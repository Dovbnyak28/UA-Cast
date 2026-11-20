# M3U: виправлення CPU-зависання та скасування імпорту

Дата: 6 вересня 2026. Продовження перевірки продуктивності плеєра й великих плейлистів.
Обсяг цього проходу — M3U parser і його наявний CPU dispatcher, не нова сертифікація всього застосунку.

## Підтверджена проблема — P1

У `M3uParser` неякірний regex атрибутів повторно перевіряв кожен суфікс довгого ключа без `=`.
На Mi A2 подвоєння довжини такого токена збільшувало час приблизно вчетверо:
1 000 символів — 25 мс, 2 000 — 100 мс, 4 000 — 411 мс, 8 000 — 1 591–1 635 мс.
Це відтворено на синтетичних даних, без мережі та особистих плейлистів.

Шлях виконання: URL/file loader → `PlaylistRepository.toOutcome` →
`withPlaylistCpuCancellable` → `M3uParser.parse` → EXTINF/header attribute regex.
Ліміт завантаження 8 MiB не виключає довгого окремого рядка.
Парсер перевіряв скасування лише між блоками рядків та на вході/виході; довгий regex цього не робив.
Тому скасований імпорт міг продовжувати займати єдиний playlist CPU worker і затримувати наступний імпорт.
Це не доведений Main-thread ANR: проблемна робота вже була винесена з Main.

Перед зміною production-коду тест `long single EXTINF line observes cancellation inside the line`
впав: третя перевірка скасування всередині одного рядка не відбулася.

## Виправлення

- Заміна regex на локальний `M3uAttributeReader`: просування по токенах без повторного пошуку
  на кожному суфіксі; лінійна обробка некоректного ключа, quoted/bare значень.
- Перевірки скасування під час пошуку коми та сканування довгих атрибутів/header.
  Виняток власника не перехоплюється, частковий результат не публікується.
- Читання EXTINF/header у межах початкового рядка без копій усього content/attributes section
  і без regex match/group об’єктів на кожен атрибут.
- Збережено aliases, дублікати з правилом «останній перемагає», blank → null, лапки,
  коми у значеннях, malformed-quote fallback, кириличні/іспанські значення та Android Unicode semantics.

Перша версія нового сканера пройшла host JVM corpus, але окремий Android-тест викрив різницю
Unicode-класів JVM та ICU. Цю регресію виправлено до фінального прогону: Unicode continuation,
combining marks, join controls, supplementary letters і whitespace перевіряються на Mi A2.
JVM-порівняння використовує Unicode-режим legacy regex, що відповідає цій Android-семантиці.

`ChannelGrouper`, ліміти імпорту, мережа, UI та диспетчери цього разу не змінювалися.
Старе виправлення скидання abandoned EXTINF headers у dirty worktree збережено.

## Виміри на Mi A2

Android 11 / API 30, debug ART. Той самий генератор; 500 каналів warmup;
по 3 виміри для 3 000 / 10 000 / 40 000 каналів; без примусового GC.
Для 40 000 каналів input = 5 551 168 UTF-8 bytes, тобто нижче чинного ліміту імпорту.

| Метрика | До | Після | Окремий повтор після |
|---|---:|---:|---:|
| Некоректний ключ 8 000 символів, 2 samples | 1 591–1 635 мс | 0,380–0,422 мс | 0,377–0,383 мс |
| Parse + group 40 000, медіана 3 samples | 2 355 мс | 553 мс | 525 мс |
| Process allocated bytes, медіана 40 000 | 78 610 480 | 28 409 888 | 28 393 504 |

На цьому синтетичному workload час скоротився приблизно у 4,3 раза, сумарні алокації — на 64%.
Це не гарантія прискорення всього застосунку або всіх provider playlists.
Частоти CPU/температура не фіксувалися. Алокації — лічильник усього процесу, не peak heap,
не retained heap і не доведення роботи в 128 MB heap. ART GC time — накопичений час, не окремі паузи.

Сирі синтетичні звіти:

- `shots/playlist-parser-fixes-2026-09-06/before/`
- `shots/playlist-parser-fixes-2026-09-06/after/`
- `shots/playlist-parser-fixes-2026-09-06/repeat/`

У кожній папці — `malformed-attribute-cost.txt` та `playlist-device-measurement.txt`.
Prefetch selection не змінювався; його перший sample у повторі становив 17 мс, наступні — 1/1 мс.

## Перевірки фінального коду

- **2 093 JVM/Robolectric test cases:** app 1 979, core 114; failures/errors/skipped = 0.
  `verifyRoborazziDebug` пройшов; screenshots/goldens у цьому проході не змінювалися.
- **13 нових host regression/diagnostic tests:** 12 parser tests, один тест фактичного parser cancellation
  на production CPU dispatcher з перевіркою доступності worker для replacement та відсутності старого результату.
  Corpus містить 4 000 детермінованих комбінацій; окремо перевірено malformed token на 1 MiB.
- **31 сценарій Mi A2:** player lifecycle 13, video fit 4, UI polish 6, empty playlist 1,
  fullscreen controls 3, playlist correctness/performance 4 — усі пройшли.
  Log: `app/build/device-audit/instrumented-7c355b58323b4b309c6117024c5960ee.txt`.
- **Окремий повтор 4 playlist tests** — усі пройшли.
  Log: `app/build/device-audit/instrumented-5ba819641dda45df88cc3e2ec6b94284.txt`.
- Detekt app/core пройшов; baseline 0. Усі 9 repository guard scripts пройшли.
- `lintPlay`: 0 errors, 0 warnings, 11 informational hints.
- Debug APK/test APK та Play AAB зібрано. Фінальний gate завершився `BUILD SUCCESSFUL` за 1 хв 42 с.
  Два наявні повідомлення R8 про async parsing class resource provider залишилися;
  dependencies/configuration для їх приховування не змінювалися.
- `git diff --check` без помилок; два наявні CRLF notices стосуються інших файлів.

Phone runner у фінальному прогоні та повторі підтвердив відновлення files/shared_prefs debug-пакета.
Основний пакет не змінювався; кінцевий font scale — 1.0. Особистий плейлист не використовувався.
Приватні recovery archives не слід включати до публічних CI/GitHub artifacts.

## Post-change architecture check і межі висновку

Читання атрибутів має одного власника — parser; reader існує лише протягом обробки рядка.
Публічний API парсера та напрямки залежностей збережено. Нових scopes, threads, global caches,
Android Context references, broad suppressions або baseline entries немає.
Cancellation належить існуючому caller coroutine; групування й мережа не перенесені до UI.

**Вердикт цього проходу: підтверджений дефект обробки довгих атрибутів та пов’язану затримку
скасування виправлено; регресійні перевірки й повторні Mi A2 виміри пройшли.**

Залишаються окремими роботами: справжній peak heap/allocations profiler під час повного імпорту
та XMLTV parse, release Macrobenchmark на ізольованих даних, тривале live video/декодування,
матриця Android/OEM, реальні Chromecast/Hisense VIDAA та Google Play billing.
Ці результати не доводять відсутності всіх багів або готовності всіх зовнішніх інтеграцій.
Публікація, зміна версії, shutdown і зміни залежностей у цей прохід не входили.
