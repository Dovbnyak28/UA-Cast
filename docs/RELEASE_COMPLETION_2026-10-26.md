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
- Локальний source ZIP після додавання relink-утиліти: 11 450 147 байтів,
  SHA256 `53dbb194596c0626139bdbdb4003d10321b8a5bb3650b5b23edee6c2a86bc5d6`.
  CI генерує власний matching bundle для власної native-збірки; цей хеш не
  підміняє його. Source ZIP ще не опубліковано поруч із новим application APK.
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
- Остаточний CI: результат доповнюється після завершення jobs для
  `652f7f00ea4fea0dbe41f9eabc78b114c1303baa`.

## Незакриті питання

1. Підтвердити результат практичного install/decode relink на емуляторі та
   всіх CI jobs. Раніше performance gate виміряв перевищення frame-бюджету;
   ліміти не послаблювалися. Старий trace показує суттєві RenderThread/shader
   та emulator graphics synchronization slices; потрібні нові вимірювання.
2. Виконати публічне matching-source постачання саме з відповідним новим APK.
   CI artifact retention або source від іншої збірки не підміняють цю дію.
3. Отримати й виконати рішення щодо раніше розповсюджених NextLib/GPL artifacts.
   Їхні releases не видалялися, дозвіл правовласника не вигадувався, власний код
   не перелiцензовувався. Це потребує окремого ліцензійного погодження.
4. Видавець має завершити фактичні identity/seller/tax та Play Console форми,
   перевірку фінальних listing assets і застосовності прав/патентів до ринків.
   Документи підготовлені, але реєстраційні дії та юридичні рішення тестами
   не підтверджуються. Фізичний Hisense VIDAA залишається неперевіреним.

Фінальний application release gate зберігає непогоджені legal checks.
Гарантії відсутності всіх можливих багів чи претензій цей звіт не дає.
