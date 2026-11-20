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
