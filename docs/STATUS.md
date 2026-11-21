# Стан проєкту

Єдина точка входу «де ми зараз». Датовані звіти лежать у [`archive/`](archive/); цей файл
посилається на останні з них і оновлюється разом з кожним етапом.

Оновлено: 2026-11-21 (release hardening, Google Play Billing і видалення self-updater злито в `master`, PR #5; версія 0.9.8).

## Версія

- `versionName` 0.9.8, `versionCode` 17 (`app/build.gradle.kts`; CI може перевизначити через
  `-Puacast.versionCode` / `-Puacast.versionName`).
- `codex/release-0.9.6` злито в `master` (5522ac7); підняття версії — гілка `codex/release-0.9.8`.

## Що перевірено

Автоматичні gates (локально, 2026-11-21, після фаз A–E і білінгу):

| Gate | Результат |
| --- | --- |
| `testDebugUnitTest` | 3186 тестів, 0 падінь (до змін — 3149) |
| `testReleaseUnitTest` / `testPlayUnitTest` | 2476 / 2476, 0 падінь (play — з живим Play Billing) |
| `:core:test` | 133 тести, 0 падінь |
| `verifyRoborazziDebug` | 151 еталон (до змін — 115), зелено |
| `lintDebug`, `lintRelease`, `detekt` | OK |
| `check-architecture-boundaries.sh`, `check-doc-references.sh` | OK |
| `check-max-file-lines.sh` | 3 файли > 500 рядків (див. нижче) |
| 16 KB page size (Play) | усі `.so`: zip-align і ELF `p_align` = 16384 |

Останні звіти перевірок на пристроях:

- [Cinema V2, етап 5 — Samsung](archive/CINEMA_V2_STAGE5_SAMSUNG_2026-11-14.md): 224/230
  instrumented-тестів без падінь, фінальні локальні перевірки й APK.
- [Cinema V2, етап 4](archive/CINEMA_V2_STAGE4_VERIFICATION_2026-11-14.md) і
  [етап 3](archive/CINEMA_V2_STAGE3_VERIFICATION_2026-11-13.md).
- [Підготовка до Play Store](archive/PLAY_STORE_SUBMISSION_2026-10-26.md),
  [завершення релізу](archive/RELEASE_COMPLETION_2026-10-26.md),
  [стабілізація CI](archive/CI_STABILIZATION_2026-10-27.md).

## Release hardening (2026-11-20/21)

Маркери фаз A–F з цифрами — у кінці
[`archive/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md`](archive/CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md).

- A: 10 з 13 великих файлів розбито; `CastSessionRepository`, `PlayerViewModel`, `ProxyServer` — потрібне
  дизайн-рішення (lifecycle-оркестрація).
- B: ця сторінка; 106 звітів у `archive/`.
- C: [оновлення 0.9.3 → 0.9.7 без втрати даних](archive/RELEASE_UPGRADE_VERIFICATION_2026-11-20.md) (API 36);
  підписаний реліз з HEAD і API 30 — за власником.
- D: [базова лінія продуктивності](archive/PERFORMANCE_BASELINE_2026-11-20.md), бюджети 10k/40k.
- E: матриця 320dp / 200% / 48dp; виправлено обрізані заголовки налаштувань.
- Self-updater (GitHub APK) прибрано з усіх збірок разом з `REQUEST_INSTALL_PACKAGES` (2026-11-21).
- Білінг: [Google Play Billing увімкнено в play-збірці](archive/PLAY_BILLING_VERIFICATION_2026-11-21.md),
  `premium_lifetime` = 3,99 EUR (ціна — у Play Console).

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
- Підписаний реліз з HEAD і оновлення на API 30 не виконані (фаза C);
  оновлення 0.9.3 → 0.9.7 на API 36 пройдено.
- Версію піднято до 0.9.8 / versionCode 17 (0.9.7 уже опублікована на GitHub 24.09).
- Політика конфіденційності оновлена 2026-11-21 (прибрано перевірки оновлень з GitHub): опубліковані сторінки
  треба передеплоїти з `legal/`, а хеші в `config/legal/release-readiness.json` (`public_privacy_deployment`)
  оновити; `signed-release` також чекає на записане схвалення продажів (`commerce`).
- Реальна покупка Premium (3,99 €), acknowledge, restore і refund перевіряються лише на тестовому треку Play
  з ліцензованим тестувальником.
- Два інструментальні тести на API 36 (`UiPolishInstrumentedTest.landscapeNavigation…`,
  `CinemaPlayerInstrumentedTest.everyThemeKeepsTransport…`) падають на емуляторі й у CI ще з базового коміту.
- Мережеві тести проксі (`ProxyHttpServerTest`, `ProxyRendererProfileTest`) зрідка падають за
  таймаутом сокета, коли debug/release/play тести йдуть паралельно; ізольовано проходять.

## Наступні кроки

1. Власнику: підписати й завантажити `bundlePlay` на internal testing, пройти чекліст покупок з
   `RELEASING.md`.
2. Ручні перевірки на реальному обладнанні (розділ «Ручні перевірки» плану): Chromecast, DLNA
   (Hisense VIDAA), Android TV / Mi TV, PiP і повороти на бюджетних Xiaomi, встановлення
   підписаного релізу поверх попереднього.
