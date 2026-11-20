# Контрольна стабілізація — 27.10.2026

PR: https://github.com/Dovbnyak28/UA-Cast/pull/4.
Candidate: `ac98e5d01142bbc6d5fc22da5fdd4180e8b344fa`.
За вказівкою власника використано локальні перевірки й GitHub CI; фізичні телефони
та Xiaomi TV не використовувалися. Application release і merge у master не виконано.

## Зміни

- Cast-кнопка використовує офіційний async module setup на спільному IO executor.
  SDK отримує application context, сам View — themed UI context. Регресійний тест
  падав до зміни й проходить після, перевіряючи actual threads і context ownership.
- Snapshot fixtures мають власний filesDir; видалення snapshot перевіряється
  також за наявності стороннього diagnostic-файлу. Socket fixtures завершують workers.
- Fullscreen regression перевіряє збереження PlayerView/TextureView, нові video
  frames та декодовані сірі пікселі після трьох переходів і resume. Внутрішній
  SurfaceTexture може перестворюватись Android. Cleanup перевіряється після
  фактичного видалення mini-player та завершення Compose disposal.
- Controls measurement fixture має фіксовану landscape геометрію. Production
  sensor transitions окремо перевіряються lifecycle suite.

## Перевірки

- Локально: дев'ять Cast/player/layout tests, detekt та native test compilation — pass.
  Snapshot/icon regressions пройшли Debug/Release/Play. Universal Debug APK і Play
  AAB зібрано; lint — 0 errors, 0 warnings, 9 hints. Play updater/permission guard — pass.
- APK/AAB: 15 native libraries, шість licence assets і legal HTML відповідають
  джерелам; немає вбудованих плейлистів/відео чи старого libmedia3ext.so.
- SHA256 universal Debug APK: `a9d6b6c535f25216efb6124cec1898bdee95a17dba17bdc7d31859be9856a84d`.
  Це тестова збірка, не новий публічний release.
- `3f862be`: Debug/screenshots 2814, Release 2365, Play 2365, core 133,
  decoder Debug/Release по 5; failures/skips — 0. Quality та packaging pass.
  Native decode/pixels пройшли API 24/30/36, але teardown assertion перед disposal
  впав. Останній test-only commit виправляє очікування; production дерева,
  ресурси та залежності не змінені (перевірений Git diff).
- Candidate CI: [Android](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40420710427),
  [performance](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40420710452).
  Android PR run і незалежний [push run](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40420702087)
  **успішні**, всі сім jobs кожного. Фінальні unit counters: 2814 Debug,
  2365 Release, 2365 Play, 133 core, 5+5 decoder; failures/skips — 0.
  API24/30/36: app 162/165/165 (по шість спеціальних assumptions), decoder по 15;
  API30 recipient-key relink — 1 wiring + 15 decode. Fullscreen pixels/resume/cleanup pass.
  Performance attempt 1 зупинився до запуску емулятора на пошкодженому SDK Emulator ZIP;
  повторено тільки цей job на тому самому SHA. Attempt 2 **успішний**: усі вісім
  journeys та 14 metric/budget checks пройшли. Числові ліміти не збільшувалися.

## Вимірювана продуктивність

Повний попередній [API30 run, attempt 2](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40418231238/attempts/2)
на тому самому production-коді містить усі вісім journeys. Перша спроба зупинилась
до build/tests на пошкодженому SDK CMake ZIP. Жоден числовий бюджет не збільшено.

| Сценарій | Попередній run | Фінальний run | Бюджет |
|---|---:|---:|---:|
| Cold startup, median | 393.728 ms | 314.871 ms | 5000 ms |
| Warm startup, median | 86.415 ms | 67.368 ms | 2500 ms |
| Cold restore 40k, median | 395.796 ms | 302.278 ms | 10000 ms |
| Відкрити 40k каналів, frame P95 | 59.604 ms | 52.503 ms | 100 ms |
| Перший player, frame P95 | **146.719 ms — fail** | 75.294 ms | 100 ms |
| Fullscreen, frame P95 | 96.987 ms | 85.908 ms | 100 ms |
| EPG UI, frame P95 | 48.810 ms | 39.427 ms | 100 ms |
| XMLTV 350k + index, median | 1971.101 ms | 1198.896 ms | 60000 ms |

Усі heap budgets обох runs пройшли; фінальний XMLTV maximum — 114013 KiB при CI heap 256 MiB.
Це не сертифікація всіх пристроїв із heap 128 MiB.

Аналіз усіх п'яти player traces: найбільший UI frame першої ітерації — 312.990 ms,
з них Running — 11.737 ms, sleeping — 295.321 ms; поруч зафіксовані GPU wait
264.336 ms та concurrent GC 308.646 ms. У решті чотирьох ітерацій максимум
UI frame — 43.780–60.705 ms. Це локалізує залишковий пік, але не доводить, що
винен лише емулятор. Невдалий P95 не відкинуто й не замінено зручнішою метрикою.
Стандартна frame CPU metric включає RenderThread; п'ять runs мають по п'ять samples.
Їхні maxima: 331.116, 96.004, 77.391, 100.670, 81.347 ms. Отже, крім першого піку,
є ще один frame трохи вище 100 ms; UI-thread duration не підміняє gate metric.
Async setup прибирає синхронний module setup; frame-time speedup не доведений.

## Вердикт і залишок

**Публічний реліз поки не готовий.**

1. Фінальний performance gate зелений, але той самий production-код на іншому
   shared runner дав перевищення. Це не доведений app speedup: навіть XMLTV
   median змінився 1971→1199 ms без production-змін. Варіативність first-player
   GPU/GC потребує контрольованих вимірів; старі невдалі runs не відкинуто.
2. Strict legal gate повертає exit 1 тільки для `historical_gpl_distribution`.
   Старі NextLib/GPL APK потребують погодженого ліцензійного вирішення; поточна
   заміна бібліотеки не врегульовує попереднє розповсюдження. Старі releases не видалені.
3. Видавець має завершити фактичні Spain identity/seller/tax і Play Console дії.
   Продажі вимкнені. Чеклісти й документи підготовлені, але реєстрації не вигадані.
4. Реальна сумісність з проблемним Hisense VIDAA та фізична TV/phone регресія
   цим CI не підтверджуються. Xiaomi TV не використовувався.

Попередні докази й юридичні посилання:
[звіт стабілізації](RELEASE_COMPLETION_2026-10-26.md),
[NextLib remediation](HISTORICAL_NEXTLIB_REMEDIATION_2026-10-26.md),
[Spain checklist](PUBLISHER_SPAIN_CHECKLIST_2026-10-26.md).
