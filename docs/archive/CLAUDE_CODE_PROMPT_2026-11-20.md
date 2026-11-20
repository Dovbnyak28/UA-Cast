# Claude Code: продовження UA CAST після аудиту 2026-11-20

> ⚠️ READ FIRST. Цей промпт — продовження аудиту, зробленого в Cowork 20.11.2026.
> Виконуй кроки строго по черзі. Після кожної фази допиши в кінець
> `docs/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md` маркер `<!-- PHASE-X DONE -->` з фактичними
> цифрами gates (кількість тестів, результат lint/detekt). Фаза без маркера = не виконана.
> Якщо щось неможливо — `<!-- PHASE-X BLOCKED: причина -->`, і переходь далі. Нічого не пропускай мовчки.

## Контекст (перевір, не вір на слово)

- Репозиторій: `D:\Projects\UA CAST`, гілка `codex/release-0.9.6`, версія 0.9.7 (versionCode 16).
- 9 локальних комітів `1ecb916..` (верхній — «docs: Claude Code continuation prompt») ще НЕ запушені (у Cowork push заблоковано).
- Стан на 14.11: debug 3146, release/Play 2476 unit-тестів, 115 screenshot-еталонів, lint/detekt OK,
  Samsung 224/230 instrumented без падінь.
- Сторонні файли (diary web app, VM) перенесено в `_unrelated/` (ignored) — не повертай їх у git.
- План фаз A–F і чекліст ручних перевірок: `docs/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md`.
  Прочитай його повністю перед стартом.
- Правила проєкту: файли < 500 рядків, без глобального стану для per-receiver рішень, bounded memory,
  тільки задокументовані поетапні зміни, без великих структурних рефакторів «заодно».

## Крок 0: синхронізація і базова точка

1. `git status` → має бути чисто. `git log --oneline -10` → верхній коміт «docs: Claude Code continuation prompt for release hardening».
2. `git push origin codex/release-0.9.6`. Якщо push не пройшов — зупинись і скажи чому.
3. Прогони gates і зафіксуй базові цифри ДО змін:
   `./gradlew testDebugUnitTest testReleaseUnitTest testPlayUnitTest verifyRoborazziDebug lintDebug lintRelease detekt :core:test`
   + `bash scripts/check-architecture-boundaries.sh` + `bash scripts/check-doc-references.sh`
   + `bash scripts/check-max-file-lines.sh` (очікувано 13 файлів > 500 рядків).
   Якщо базові gates червоні — спершу полагодь це окремим комітом, і тільки потім фаза A.

## Кроки 1–6: фази A–F

Виконуй фази A, B, C, D, E, F з `docs/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md` у тому ж порядку.
Додатково до написаного там:

- **Фаза A** — по одному файлу за раз: extract → gates → коміт `refactor: split <File> (no behaviour change)`.
  Почни з найменш ризикових (`AppIcons.kt`, `DesignSystemControls.kt`, `GuidedTourOverlay.kt`),
  `PlayerViewModel`/`CastSessionRepository`/`ProxyServer` — останніми. Якщо змінилась кількість тестів
  або хоч один тест падає — відкоти цей файл, а не «підганяй» тест.
- **Фаза C** — release keystore лежить у корені як `a-cast-player-release` (ignored). Ніколи не додавай
  його в git і не виводь паролі в лог; паролі бери тільки зі змінних оточення / `local.properties`.
- **Фаза D/E** — якщо потрібного емулятора/пристрою немає, познач фазу BLOCKED з точною командою,
  яку власник має запустити, і переходь далі.
- **Фаза F** — PR створи через `gh pr create`, гілки не видаляй.

## Наприкінці

1. Онови `docs/STATUS.md` (з фази B) фактичним станом.
2. `git push`.
3. Дай короткий звіт: які фази DONE / BLOCKED, цифри gates до і після, що лишилось власнику
   з розділу «Ручні перевірки на реальному обладнанні».
