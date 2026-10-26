# Завершення стабілізації, 26.10.2026

Продовження [попереднього звіту](PLAY_COMPLIANCE_STABILIZATION_2026-10-25.md).
Xiaomi TV за прямою вказівкою власника не використовується для перевірок цього
етапу. Нового application release або змін у `master` цим етапом не виконано.

## Виконано

- Android-зміни опубліковані в існуючий
  [PR №4](https://github.com/Dovbnyak28/UA-Cast/pull/4); сторонні diary/web файли,
  приватні ключі, локальні логи й APK до git-коміту не включені. Робочу гілку
  користувача й його індекс збережено; для публікації використано окремий індекс.
- Підтверджене падіння CI `sdkmanager: command not found` усунуте: Android jobs
  закріплені за Ubuntu 24.04, native workflow викликає SDK manager явним шляхом.
- Незалежний decoder build і source bundle проходять CI. Окремий test-only
  build змінює licence-query рядок FFmpeg, перебудовує `libavcodec.so` і перевіряє,
  що production native hashes лишилися незмінними.
- Додано утиліту заміни `.so` в APK: зберігає application DEX/resources,
  прибирає старі підписи, відхиляє неправильний ABI, дублікати/небезпечні ZIP paths
  та ідентичну заміну. Виявлена Windows-нормалізація backslashes врахована.
- Справжній локальний APK перепакований із модифікованою CI-бібліотекою,
  вирівняний на 16 KiB і підписаний окремим тестовим ключем. `apksigner verify`
  підтверджує підписи v2/v3. Встановлення на фізичні пристрої не виконувалось.
- CI-сценарій relink приймає тільки serial виду `emulator-N` і перевіряє
  `ro.kernel.qemu=1` до будь-якого uninstall/install. Перевіряє app wiring та
  реальне декодування через окрему бібліотечну instrumentation suite.
- У privacy/terms внесено погоджене публічне ім'я **ROMAN DOVBNYAK**.
  Додано іспанську privacy, права щодо персональних даних, підставу support-
  обробки й контакт AEPD. Чотири дозволені language anchors не відкривають
  доступ до інших файлів або мережевої навігації в offline WebView.
- [Вимоги до видавця з Іспанії](PUBLISHER_SPAIN_CHECKLIST_2026-10-26.md),
  [матеріали Play/Data safety](PLAY_STORE_SUBMISSION_2026-10-26.md) і
  [досьє історичних NextLib-релізів](HISTORICAL_NEXTLIB_REMEDIATION_2026-10-26.md)
  підготовлені. Продажі залишаються вимкненими до операційної/податкової перевірки.

## Документи та артефакти

Pages commit `f041239c703dda70d34d05bd239932107d019cd7` розгорнутий; privacy/terms
на обох root/legal адресах повернули HTTP 200 і збігаються побайтно з проєктом.

- Privacy SHA256 `814c837ac62848b168b35e75aaf1af194a960c5cfde51f7e229f9248bd776645`.
- Terms SHA256 `4f513031e804355522c58165e6409166f2448a1a4bb78507981d35c12b9e39ae`.
- Локальний source ZIP після додавання relink-утиліт і дозволу на build scripts:
  11 451 989 байтів, SHA256
  `3da9ed32c534af3daeaa115a061fb6f7d1804d0733448e02b399af13935570bb`.
  CI генерує власний matching bundle для власної native-збірки; цей хеш не
  підміняє його. Комплект публічно опубліковано окремим source-only prerelease:
  https://github.com/Dovbnyak28/UA-Cast/releases/tag/decoder-source-2026-10-26.
  Анонімне завантаження — HTTP 200, checksum збігається. У наступному app release
  необхідно також опублікувати саме відповідний його збірці комплект.
- Фінальний Debug APK — 40 454 381 байтів; Play AAB — 19 156 410 байтів.
  15 native-бібліотек у кожному перевірені за завантажуваними ELF-секціями,
  6 ліцензійних assets і юридичні HTML відповідають джерелам.

## Перевірки

- Python scripts: **39/39**; у тому числі source/native/relink і legal guards.
- Kotlin/Java instrumentation після змін relink — компілюються.
- Privacy anchors і licence reader/UI — локальна регресія пройшла.
- Фінальна збірка Debug APK/Play AAB — **BUILD SUCCESSFUL**, 4m 37s.
- Play lint — **0 errors, 0 warnings, 9 informational hints** в останньому
  offline-прогоні; попередній online-прогін мав 27 informational hints.
- Play distribution guard і legal-assets перевірка — успішні.
- CI job `121801087090`: Android 11/API 30 виконав app suite (`OK (164 tests)`;
  LAN/спеціальні device-only сценарії мають власні assumptions), базові decoder
  тести 15/15, потім recipient-signed relink app wiring 1/1 і decoder 15/15.
- CI на `652f7f0` знайшов race в unit-тесті закриття file descriptor та
  API-26-only screenshot helper на API 24. Це виправлено без зміни production
  proxy cleanup: тест чекає на сигнал закриття; необов'язковий PixelCopy capture
  має API guard. Локально 9/9 у Debug, Release і Play, instrumentation компілюється.
- Cold-restore benchmark очікував точну назву картки, яка опинилась нижче fold.
  Тепер чекає видимий source selector, доступний лише після `hasChannels=true`.
  Обидва benchmark variants компілюються.
- Коміт `0101fe839aadabed59a77b00ef36edf6bd23ac02`: push CI
  [40392740945](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40392740945)
  і PR CI [40392749775](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40392749775)
  успішні: core 133, Debug/screenshot 2804, Release 2363, Play 2363 unit tests;
  API 24/30/36 app suites — 161/164/164 tests (по 6 assumptions для спеціальних
  сценаріїв), decoder — по 15, recipient-key relink на API 30 — 1 wiring + 15 decode.
- Повний performance run із `swangle` виявив незавершені FrameTimeline slices,
  тому не прийнятий як доказ продуктивності. Окремий контрольний run
  [40396418355](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40396418355)
  порівняв три renderers на незміненому app commit: `swiftshader`,
  `swiftshader_indirect`, `lavapipe`. Усі три дали повні measurements;
  deprecated indirect не повертається в основний gate. Обраний явний `lavapipe`.
  Його P95: відкриття 40k — 22.69 ms, player — 104.06 ms, fullscreen — 114.82 ms,
  EPG — 31.33 ms. Зміна emulator backend не є app speedup; ліміти не збільшені.
- Під непрозорим expanded player головний scaffold тепер зберігає composition,
  навігацію та Android views, але не виконує приховане малювання. Видимість
  пов'язана з фактичним завершенням opening animation; при collapse/close
  малювання відновлюється. Request token не утримує попередній список каналів.
  Локальні тести перевіряють drawing, animation, native-view retention і перший
  opaque frame при disabled motion; разом із player/layout/motion — 21 tests.
  Detekt та compilation instrumentation успішні. Повний CI і вимірювання
  останньої оптимізації мають підтвердити ефект на тому самому `lavapipe`.
- Decoder buffer unit tests додані до Debug/Release CI та signed-release gate:
  локально 5/5 у кожній конфігурації, раніше CI запускав лише instrumentation
  цього модуля.
- Остаточні CI results потрібно звіряти із SHA останнього коміту
  [гілки PR №4](https://github.com/Dovbnyak28/UA-Cast/pull/4/checks).
  Старі невдалі або скасовані прогони не підтверджують стан наступних комітів.

## Незакриті питання

1. Завершити повний CI та всі вісім performance measurements для останньої
   underlay-оптимізації. Практичний recipient-key install/decode relink на
   API 30 вже успішний. Бюджети не послаблювалися; результат зміни renderer
   відокремлюється від ефекту production-коду.
2. Для наступного application release виконати публічне matching-source
   постачання саме його збірки. Технічну готовність джерел/заміни бібліотеки
   підтверджено; наявність публічного source-only архіву не дозволяє підміняти
   відповідний комплект іншої native-збірки.
3. Отримати й виконати рішення щодо раніше розповсюджених NextLib/GPL artifacts.
   Їхні releases не видалялися, дозвіл правовласника не вигадувався, власний код
   не перелiцензовувався. Це потребує окремого ліцензійного погодження.
4. Видавець має завершити фактичні identity/seller/tax та Play Console форми,
   перевірку фінальних listing assets і застосовності прав/патентів до ринків.
   Документи підготовлені, але реєстраційні дії та юридичні рішення тестами
   не підтверджуються. Фізичний Hisense VIDAA залишається неперевіреним.

Технічний `native_corresponding_source` readiness підтверджено публічним комплектом
для перевіреного локального кандидата та фактичним relink/decode. Фінальний
application release gate зберігає непогоджений `historical_gpl_distribution`.
Гарантії відсутності всіх можливих багів чи претензій цей звіт не дає.
