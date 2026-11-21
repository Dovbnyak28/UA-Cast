# Базова лінія продуктивності — 2026-11-20

Фаза D плану [release hardening](CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md).

## Середовище

- AVD `medium_phone`: Android 16 (API 36), x86_64, `hw.ramSize` 2048 МБ (`MemTotal` 2 531 840 kB), GPU емулятора
  за замовчуванням, headless (`-no-window -no-snapshot`). Хост: Windows 11, 16 потоків, WHPX.
- Збірка `benchmarkRelease` (R8, Partial compilation), HEAD `30403b2` + 10k-фікстура з цього етапу.
- Команда:
  `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=…StartupBenchmark,…CriticalJourneysBenchmark,…MediumCatalogBenchmark -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`
- Gradle завершився з `Failed to receive the UTP test results` (збій звітування UTP), але
  `benchmarkData.json` містить повні серії для всіх 14 сценаріїв; саме він є доказом.
- `EpgParseBenchmark` (350 000 програм) у цей прогін не входив.

## Результати

Startup — медіана з 10 ітерацій; решта — 5 ітерацій. Кадри — `frameDurationCpuMs` (FrameTimeline, API 31+).

| Сценарій | Ключова метрика | Пік Java heap |
| --- | --- | --- |
| Cold startup | TTID медіана **1495 мс** (1448–1847) | — |
| Warm startup | TTID медіана **927 мс** (231–985) | — |
| Cold restore 40 000 каналів | TTID **1523 мс** | **27.3 МБ** |
| Cold restore 10 000 каналів | TTID **1179 мс** | 23.7 МБ |
| Відкриття каталогу 40k | кадри P50 41.9 / P95 **134.3** мс | 43.7 МБ |
| Відкриття каталогу 10k | кадри P50 31.2 / P95 **105.9** мс | 25.8 МБ |
| Пошук у 40k (перший результат) | кадри P95 **30.1** мс | 42.4 МБ |
| Пошук у 10k | кадри P95 **26.1** мс | 26.6 МБ |
| Скрол великої групи | кадри P50 37.3 / P95 **49.4** мс | 47.0 МБ |
| Перший запуск плеєра | перший відеокадр **639 мс**, кадри P95 229.5 мс | 46.9 МБ |
| Перемикання каналу | перший відеокадр **255 мс**, кадри P95 87.4 мс | 48.8 МБ |
| Вхід у fullscreen (показ/приховування контролів) | кадри P95 **100.6** мс | 48.7 МБ |
| Відкриття EPG-гіда | кадри P95 **121.8** мс | 46.7 МБ |

Пік Java heap під час завантаження 40k-плейлиста — **≤ 49 МБ** у всіх UI-сценаріях і 27 МБ на холодному
відновленні; бюджет heap у CI — 256 МБ.

## Висновки

- Startup, відновлення 40k, пошук, скрол і перемикання каналу — з великим запасом.
- Вартість каталогу росте з розміром помірно: 10k → 40k додає ~29% до P95 відкриття і ~0.35 с до cold restore.
- P95 кадрів на **переходах між екранами** (відкриття каталогу, перший плеєр, EPG, fullscreen) — 100–230 мс:
  це поодинокі кадри композиції нового екрана. На API 30 + lavapipe у CI діє бюджет 100 мс; цифри цього прогону з
  ним не порівнювані (інший API, джерело таймінгу та GPU), але переходи — перший кандидат на оптимізацію.
- Вимір на реальних 2–4 ГБ пристроях (Mi A2, бюджетний Xiaomi) лишається за власником.

## Бюджети

- Новий [`config/performance/local-api36-2gb.json`](../../config/performance/local-api36-2gb.json) — ~1.5× від
  цих вимірів, для локальної регресії на цьому AVD:
  `python scripts/check-performance-budgets.py baselineprofile/build/outputs --budgets config/performance/local-api36-2gb.json`
  → `All required performance budgets passed`.
- [`config/performance/ci-api30.json`](../../config/performance/ci-api30.json) доповнено трьома 10k-сценаріями з
  тими самими лімітами, що й 40k; `MediumCatalogBenchmark` додано до `performance-gate.yml`;
  `scripts/tests/test_performance_budgets.py` оновлено (14 сценаріїв).
