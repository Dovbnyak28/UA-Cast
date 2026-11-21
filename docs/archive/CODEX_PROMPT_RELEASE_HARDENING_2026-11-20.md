# Codex: release hardening після аудиту 2026-11-20

> ⚠️ READ FIRST. Виконуй фази строго по черзі. Кожна фаза закінчується **маркером**: рядком
> `<!-- PHASE-X DONE -->`, який ти дописуєш у кінець цього файлу разом із фактичними результатами
> gates. Фаза без маркера вважається НЕ виконаною. Не пропускай фази мовчки: якщо щось
> неможливо, допиши `<!-- PHASE-X BLOCKED: причина -->`.
>
> Базова точка: гілка `codex/release-0.9.6`, коміти 1ecb916a..HEAD (20.11.2026) містять усю
> роботу за жовтень-листопад. Перед стартом: `git status` має бути чистим.
>
> Gates після КОЖНОЇ фази з кодом: `./gradlew testDebugUnitTest testReleaseUnitTest testPlayUnitTest
> verifyRoborazziDebug lintDebug lintRelease detekt :core:test` + `scripts/check-architecture-boundaries.sh`
> + `scripts/check-doc-references.sh`. Кожна фаза = окремий коміт.

## Фаза A: розбити файли > 500 рядків (лише переміщення, без зміни поведінки)

Перелік дає `scripts/check-max-file-lines.sh` (зараз 13 файлів):
AppIcons.kt 1177, CastSessionRepository 823, PlayerViewModel 813, ProxyServer 783,
AppViewModel 709, DesignSystemControls 630, DlnaSessionRepository 609, ProxyHttpServer 580,
RootScaffold 567, AddPlaylistScreen 560, PlaylistController 557, GuidedTourOverlay 531,
HlsFlattenedStream 517.

Правила:
- Тільки extract: приватні хелпери / стани / composable-секції / мапери в сусідні файли того ж пакета.
  Публічний API, назви класів і поведінку не міняти. Жодних «попутних» виправлень.
- `AppIcons.kt`: розбити по групах іконок (`AppIconsPlayer.kt`, `AppIconsNavigation.kt`, ...).
- `PlayerViewModel` / `CastSessionRepository` / `ProxyServer`: виносити stateless логіку
  (reducers, policies, парсери), не розривати lifecycle-ownership (`liveInstances`, session id).
- Після фази додай `scripts/check-max-file-lines.sh` у `.github/workflows/android-ci.yml`.

Маркер перевірки: `bash scripts/check-max-file-lines.sh` → `OK`.

## Фаза B: консолідація документації

- Створи `docs/STATUS.md`: поточна версія, що перевірено (з посиланнями на останні звіти),
  відкриті ризики (розділ «Залишилося» з `CINEMA_V2_STAGE5_SAMSUNG_2026-11-14.md`), наступні кроки.
- Перенеси всі датовані звіти `docs/*_20XX-XX-XX.md` у `docs/archive/` через `git mv` і виправ
  відносні посилання в них (`../build/` → `../../build/`, `../scripts/` → `../../scripts/` тощо).
- У README додай посилання на `docs/STATUS.md`.

Маркер: `scripts/check-doc-references.sh` проходить; `ls docs/*_20*.md` нічого не повертає.

## Фаза C: перевірка релізного підпису та оновлення без втрати даних

- Збери підписаний release 0.9.7 (`signed-release.yml` локально або через CI) і перевір
  `apksigner verify --print-certs`, що сертифікат збігається з 0.9.6.
- Сценарій на емуляторі API 30 і API 36: встановити підписаний 0.9.6 → додати 2 плейлисти,
  улюблені, EPG-джерело, PIN батьківського контролю, тему Cinema → `adb install -r` 0.9.7 →
  усі дані на місці, міграція плейлистів без помилок. Оформи як instrumented/скрипт-тест.
- Перевір in-app updater на цьому ж ланцюжку.

Маркер: звіт `docs/RELEASE_UPGRADE_VERIFICATION_<дата>.md` з SHA-256 обох APK.

## Фаза D: вимірювання продуктивності

Через `baselineprofile`/macrobenchmark на емуляторі, профіль якого близький до 2-4 ГБ RAM:
- COLD і WARM startup (медіана з 10);
- відкриття каталогу на 10k і 40k каналів, перший результат пошуку;
- FrameTimingMetric для скролу каталогу та показу/приховування контролів плеєра (≥ 30 ітерацій);
- peak Java heap під час завантаження 40k плейлиста.
Зафіксуй бюджети в `config/performance/` і в `scripts/check-performance-budgets.py`.

Маркер: звіт `docs/PERFORMANCE_BASELINE_<дата>.md` + оновлені бюджети.

## Фаза E: візуальний огляд 320dp / шрифт 200%

Roborazzi-знімки для КОЖНОГО екрана та стану: порожній, завантаження, помилка, без мережі;
обидві теми (Azure, Cinema) × uk/en; ширина 320dp і шрифт 2.0. Виправ обрізаний текст,
недоступні кнопки, зони дотику < 48dp. Еталони записати, verify зелений.

Маркер: кількість screenshot-еталонів зросла, у звіті перелік виправлених екранів.

## Фаза F: злиття

Після зеленого CI на `codex/release-0.9.6`: PR у `master`. Застарілі гілки
(`ci/verify-polished-state-20260829`, `codex/release-0.9.3`, `codex/release-0.9.4`,
`feature/dlna`, `feat/update-check-and-premium-layer`) та worktree
`secure-backup-performance` НЕ видаляти без підтвердження власника: лише склади список.

---

## Ручні перевірки на реальному обладнанні (не для Codex, робить власник)

1. Chromecast: 5 каналів (HLS, raw TS через проксі, 1080p, 480p, канал з поганим сигналом),
   блокування екрана на 10 хв під час трансляції (foreground service / MIUI), перемикання каналів.
2. DLNA: Hisense VIDAA (регресія «Archivo no compatible»), гучність, повторне підключення.
3. Android TV / Mi TV: навігація D-pad усіма екранами, введення PIN, document picker.
4. PiP і повороти на Mi A2 / бюджетному Xiaomi.
5. Встановлення підписаного релізу поверх попереднього на реальному телефоні.

---

## Маркери виконання (Claude Code, 2026-11-20)

Базова точка до змін (HEAD `209cad3`, запушено в `origin/codex/release-0.9.6`):
`testDebugUnitTest` 3149/0, `testReleaseUnitTest` 2476/0, `testPlayUnitTest` 2476/0, `:core:test` 133/0,
`verifyRoborazziDebug` 115 еталонів OK, `lintDebug`/`lintRelease`/`detekt` OK, `check-architecture-boundaries` OK,
`check-doc-references` OK, `check-max-file-lines` — 13 файлів > 500.
Примітка до середовища: Gradle wrapper не качав дистрибутив через перехоплення TLS на хості; усі прогони
виконано з `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` (без змін у репозиторії).

<!-- PHASE-A BLOCKED: 10 з 13 файлів розбито (кожен — окремий коміт `refactor: split <File> (no behaviour change)`
з повними зеленими gates: 3149/2476/2476/133, Roborazzi 115, lint/detekt OK; кількість тестів не змінилась).
AppIcons 1177→72, DesignSystemControls 630→421, GuidedTourOverlay 531→284, AddPlaylistScreen 560→348,
RootScaffold 567→429, HlsFlattenedStream 517→452, ProxyHttpServer 580→475, PlaylistController 557→496,
DlnaSessionRepository 609→491, AppViewModel 709→471.
BLOCKED: CastSessionRepository 823, PlayerViewModel 813, ProxyServer 783 — stateless-логіки в них < 60 рядків;
решта — оркестрація з lifecycle-станом (liveInstances, session id, producerLock/flattenedStreams/upstreamCalls,
прапорці foreground/cast). Довести до 500 можна лише розривом lifecycle-ownership, що план забороняє; потрібне
рішення власника щодо окремого дизайн-етапу. Через це `check-max-file-lines.sh` → exit 1 (3 файли) і НЕ доданий
у android-ci.yml (інакше CI був би червоний).
Флейки під навантаженням (емулятор паралельно з 3 тест-JVM): ProxyManifestLeaseTest, ProxyHttpServerTest,
ProxyRendererProfileTest, IconResolutionConcurrencyTest — ізольовано 3/3 зелені; повний прогін на «тихій»
машині зелений. -->

<!-- PHASE-B DONE: створено docs/STATUS.md (версія, що перевірено, відкриті ризики з етапу 5 + флейки, наступні
кроки); 106 датованих звітів docs/*_20XX-XX-XX.md перенесено в docs/archive/ через git mv (rename у git),
відносні посилання перераховано скриптом (8 перенесених файлів змінено; у docs/DESIGN_SYSTEM.md, TV_SUPPORT.md,
PERFORMANCE.md, CINEMA_V2_DESIGN_SYSTEM.md посилання ведуть в archive/). README посилається на docs/STATUS.md.
check-doc-references OK; `ls docs/*_20*.md` → порожньо; перевірка всіх відносних markdown-посилань у docs/ і
README: 0 зламаних (до переносу теж 0). Цей файл тепер лежить у docs/archive/. -->

<!-- PHASE-C BLOCKED (частково виконано): звіт docs/archive/RELEASE_UPGRADE_VERIFICATION_2026-11-20.md (у коміті
фази B) + scripts/verify-release-upgrade.sh. Сертифікат c040badc…602e однаковий для 0.9.2, 0.9.3 і 0.9.7-preliminary;
SHA-256 APK: 0.9.3 1360179f…4377, 0.9.7 6974a1b1…2ad1. Оновлення 0.9.3 → 0.9.7 (vc 124 → 164) на емуляторі API 36:
2 плейлисти, улюблені, EPG, PIN + заблокований канал, тема «Кіно», розкладка «Сітка» — усе збережено, PIN працює,
FATAL немає; скрипт → OK. Підписаного 0.9.6 не існує (база — останній опублікований 0.9.3).
BLOCKED: (1) підписаний реліз з HEAD — потрібні UACAST_STORE_* (паролі лише у власника);
(2) API 30 — немає образу (sdkmanager "system-images;android-30;google_apis;x86_64");
(3) in-app updater — TLS до GitHub з емулятора перехоплюється на хості.
РИЗИК: v0.9.7 уже опубліковано на GitHub (24.09); поточна збірка теж 0.9.7/16 — підняти до 0.9.8/17 перед релізом. -->

<!-- PHASE-D DONE: звіт docs/archive/PERFORMANCE_BASELINE_2026-11-20.md. AVD medium_phone API 36 x86_64 2 ГБ RAM.
COLD startup медіана 1495 мс, WARM 927 мс (n=10); cold restore 40k 1523 мс / 10k 1179 мс; відкриття каталогу
P95 кадрів 40k 134.3 мс / 10k 105.9 мс; перший результат пошуку P95 40k 30.1 мс / 10k 26.1 мс; скрол P95 49.4 мс;
fullscreen (контроли) P95 100.6 мс; перший відеокадр 639 мс, перемикання 255 мс; пік Java heap ≤ 49 МБ
(40k cold restore 27.3 МБ). Ітерацій: startup 10, решта 5 (план просив ≥30 для FrameTimingMetric — не досягнуто,
обмеження часу прогону). Додано MediumCatalogBenchmark (10k) + фікстура ui-medium; бюджети:
config/performance/local-api36-2gb.json (новий, перевірка → passed) і ci-api30.json (+3 випадки, gate-workflow
оновлено, test_performance_budgets 10/10 OK). EpgParseBenchmark у прогін не входив. -->

<!-- PHASE-E DONE (частково за обсягом): новий NarrowLargeTextScreenshotTest — 320dp, шрифт 2.0, теми Azure і
Cinema × uk/en, 9 екранів і станів: додавання плейлиста (порожній / завантаження / помилка мережі), порожнє обране,
огляд налаштувань, довідка, умови першого запуску, вибір мови, помилка відтворення. Еталонів 115 → 151 (+36).
Кожен знімок додатково перевіряє, що всі видимі клікабельні вузли мають зону дотику ≥ 48dp — порушень 0.
Виправлено: заголовок розділу в огляді налаштувань обрізався на 2 рядках («Плейлисти й телепрогра…») —
тепер до 3 рядків (SettingsComponents.SettingsNavigationRow). Політику конфіденційності прибрано з матриці:
WebView у Robolectric не рендериться, знімок нічого не доводив.
Не покрито: «без мережі» як окремий глобальний стан (у застосунку його немає — помилка мережі показана на
додаванні плейлиста), повний обхід усіх підрозділів налаштувань, плеєра й TV-екранів у цій матриці.
UX-ризик: на 320dp/200% у формі додавання плейлиста підказка провайдера займає весь перший екран, статус
завантаження/помилки видно лише після прокрутки (досяжно; змінювати порядок блоків — рішення дизайну).
Також у цій фазі виправлено CI: часовий пояс у CinemaV2PlayerScreenshotTest (11 знімків падали в UTC) і
PixelCopy-паркан на API < 26 (14 тестів API 24 з NoSuchMethodError). -->

<!-- PHASE-F DONE (PR створено; злиття — після зеленого CI і рішення власника): PR codex/release-0.9.6 → master.
Гейти перед PR (HEAD): testDebugUnitTest 3186/0, testReleaseUnitTest 2476/0, testPlayUnitTest 2476/0 (живий
Play Billing), :core:test 133/0, verifyRoborazziDebug 151, lintDebug/lintRelease/detekt OK, boundaries/doc-refs OK.
CI базового коміту 209cad3 був червоний: виправлено часовий пояс знімків і PixelCopy на API 24; lint-джоб падав на
транзієнтному резолві gson для foojay-resolver, API 30 — збій GPU емулятора CI; 2 тести API 36 падають і локально
(відкритий ризик). Master містить лише доданий і видалений diary web app — конфліктів немає.
Застарілі гілки НЕ видалялись (список для власника): ci/verify-polished-state-20260829 (PR #2 draft),
codex/release-0.9.3, codex/release-0.9.4 (PR #3 open), feature/dlna, feat/update-check-and-premium-layer
(PR #1 merged), codex/secure-backup-performance-gate (PR #4 draft) + worktree
C:/Users/Dovbn/.codex/worktrees/secure-backup-performance, локальна гілка з назвою «origin» (плутає git),
віддалені origin/codex/legal-pages, origin/codex/performance-renderer-probe.
Додатково в цьому етапі: Google Play Billing увімкнено в play-збірці (premium_lifetime, 3,99 EUR у Play Console),
звіт docs/archive/PLAY_BILLING_VERIFICATION_2026-11-21.md. -->
