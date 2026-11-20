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
