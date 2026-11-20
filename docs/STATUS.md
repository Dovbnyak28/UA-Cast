# Стан проєкту

Єдина точка входу «де ми зараз». Датовані звіти лежать у [`archive/`](archive/); цей файл
посилається на останні з них і оновлюється разом з кожним етапом.

Оновлено: 2026-11-20 (release hardening, гілка `codex/release-0.9.6`).

## Версія

- `versionName` 0.9.7, `versionCode` 16 (`app/build.gradle.kts`; CI може перевизначити через
  `-Puacast.versionCode` / `-Puacast.versionName`).
- Робоча гілка: `codex/release-0.9.6`. Злиття в `master` — після зеленого CI (фаза F плану
  [`archive/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md`](archive/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md)).

## Що перевірено

Автоматичні gates (локально, 2026-11-20, до змін фази A):

| Gate | Результат |
| --- | --- |
| `testDebugUnitTest` | 3149 тестів, 0 падінь |
| `testReleaseUnitTest` | 2476 тестів, 0 падінь |
| `testPlayUnitTest` | 2476 тестів, 0 падінь |
| `:core:test` | 133 тести, 0 падінь |
| `verifyRoborazziDebug` | 115 еталонів, зелено |
| `lintDebug`, `lintRelease`, `detekt` | OK |
| `check-architecture-boundaries.sh`, `check-doc-references.sh` | OK |
| `check-max-file-lines.sh` | 13 файлів > 500 рядків (див. «Release hardening») |

Останні звіти перевірок на пристроях:

- [Cinema V2, етап 5 — Samsung](archive/CINEMA_V2_STAGE5_SAMSUNG_2026-11-14.md): 224/230
  instrumented-тестів без падінь, фінальні локальні перевірки й APK.
- [Cinema V2, етап 4](archive/CINEMA_V2_STAGE4_VERIFICATION_2026-11-14.md) і
  [етап 3](archive/CINEMA_V2_STAGE3_VERIFICATION_2026-11-13.md).
- [Підготовка до Play Store](archive/PLAY_STORE_SUBMISSION_2026-10-26.md),
  [завершення релізу](archive/RELEASE_COMPLETION_2026-10-26.md),
  [стабілізація CI](archive/CI_STABILIZATION_2026-10-27.md).

## Release hardening (2026-11-20)

Хід фаз A–F фіксується маркерами в кінці
[`archive/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md`](archive/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md).

## Відкриті ризики

Перенесено з розділу «Залишилося»
[звіту етапу 5](archive/CINEMA_V2_STAGE5_SAMSUNG_2026-11-14.md) і доповнено:

- Повний візуальний огляд усіх підрозділів, порожніх і помилкових станів на 320dp і 200% шрифту
  ще не завершено (фаза E покриває перші екрани й стани, не всі).
- Реальні з'єднання Chromecast і DLNA та Android TV з D-pad на фізичному телевізорі не перевірені.
- На Samsung не запускалися два тести, що змінюють системну гучність (на емуляторі етапу 4 пройшли).
- WARM startup, peak heap, frame timing на достатній вибірці, час відкриття великого каталогу й
  пошуку не мають надійного вимірювання (фаза D).
- Одноразовий збій перевірки паузи під час буферизації на Samsung (системне вікно); причинний
  зв'язок не доведений.
- Релізний підпис, оновлення 0.9.6 → 0.9.7 без втрати даних і приймальне тестування реального
  медіаконтенту не виконані (фаза C).
- Мережеві тести проксі (`ProxyHttpServerTest`, `ProxyRendererProfileTest`) зрідка падають за
  таймаутом сокета, коли debug/release/play тести йдуть паралельно; ізольовано проходять.

## Наступні кроки

1. Фази C–F плану release hardening.
2. Ручні перевірки на реальному обладнанні (розділ «Ручні перевірки» плану): Chromecast, DLNA
   (Hisense VIDAA), Android TV / Mi TV, PiP і повороти на бюджетних Xiaomi, встановлення
   підписаного релізу поверх попереднього.
