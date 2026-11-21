# Оновлення іконки й логотипів — 03.11.2026

Єдиний оригінал: `design/brand/mark.svg` — екран, play і передавання,
білий/блакитний/жовтий на темному тлі. Без нових runtime-залежностей, анімацій,
шрифтів чи мережевих запитів. Старий raster master збережений як історичний оригінал.

## Ресурси

- Adaptive foreground і monochrome — Android VectorDrawable, з однаковою alpha-геометрією.
- Splash на телефоні й TV — той самий вектор; density-specific PNG overrides вилучені.
- Старі Android launcher-и — PNG 48/72/96/144/192 px, square та round, з SVG через Sharp.
- TV — filtered nodpi PNG 640×360, згенерований Android renderer з векторного оригіналу
  у 2560×1440 із єдиним filtered downscale. Декодований bitmap не перевищує 1 MiB.
- Атрибуція наявного Roboto wordmark залишилась у `assets/licenses/tv-wordmark-roboto.txt`.
- Довідку EN/UK/RU/ES виправлено: джерелами логотипів каналів є лише власні паки;
  застарілу обіцянку вбудованого джерела вилучено. У тексті збережено вимогу Premium,
  що відповідає наявному feature gate.

## Навігація до довідки

У розділі підтримки Help і повторний вступ тепер стоять перед оновленнями та Premium.
Раніше великий Premium-блок приховував кнопку довідки нижче першого екрана.
Новий instrumentation-тест `supportMakesHelpAvailableWithoutScrollingPastPremium`
відтворив проблему до зміни порядку секцій: кнопка `settings_open_help_button`
не була видима. Після зміни тест перевіряє видиму кнопку, відкриває довідку
та знаходить перше запитання. Обробники, покупки й entitlement-логіка не змінювалися.

Докази: `build/branding/support-help-before.log`, `support-help-after.log`
та скриншот `build/branding/support-help-first.png`.

## Відтворення ресурсів

1. `python scripts/generate-brand-vectors.py` — vector XML і SVG candidates у `build/branding`.
2. `node scripts/render-brand-icons.cjs` із доступним Sharp — PNG candidates у `build/branding`.
   Sharp потрібен лише інструменту розробки; Android-застосунок його не містить.
3. Переглянути PNG та скопіювати відповідні розміри у `mipmap-*`. Попередні ресурси
   цього оновлення збережено локально в `build/branding/previous-resources`.
4. Для TV виконати `recordRoborazziDebug --tests '*TvLauncherBannerTest'`.
   Після зміни вектора перший run може відхилити попередній packaged PNG:
   переглянути новий `src/test/screenshots/tv_banner_hd_master.png`, скопіювати до
   `drawable-nodpi/tv_banner_hd.png`, повторити record та обов'язково verify.
5. Переглянути screenshots, запустити verify, detekt, lint та emulator regression.

## Перевірки

- Повний unit/screenshot-прогін: app — **2817**, core — **133**, decoder — **5**;
  нуль failures/errors/skips. До app входять 12 splash/TV та 3 adaptive/monochrome tests.
- При першому rendering перевірка виявила вихід splash за safe circle;
  масштаб виправлено, обмеження не послаблювалися.
- Перевіряються повна safe-zone геометрія, antialiased coverage, однакова alpha
  кольорового/monochrome знака, native loading та піксельна відповідність TV PNG джерелу.
- Генерація чотирьох vector XML повторюється побайтово без змін.
- На ізольованому Android 16 AVD `ua-cast-branding`: app instrumentation —
  **OK (166 tests)**, з них **6 assumption-пропусків** для окремих умов;
  decoder instrumentation — **OK (15 tests)**, без пропусків.
  Перевірено реальне декодування MP4, fullscreen/resume та швидку заміну media items.
- Crash buffer емулятора після фінального прогону порожній. Це результат цього
  прогону, а не доказ відсутності всіх можливих production-crash.
- Debug APK/test APK та Play AAB зібрані; detekt і Roborazzi verify успішні.
  Debug і Play lint: **0 errors, 0 warnings, 2 hints** кожен.
- Play distribution guard успішний: у Play manifest немає sideload-updater
  та restricted install permissions.
- У кожному пакунку перевірено **15 native libraries**, **6 licence assets**
  та legal HTML проти локальних перевірених оригіналів. Немає вбудованого медіа,
  старого `libmedia3ext` чи вилучених raster overrides.
- Вилучені raster overrides займали 719 320 байтів, три vector XML — 3 979 байтів.
  Це порівняння ресурсів, не вимірювання зменшення heap чи всього APK.

Логи: `build/branding/final-validation.log`, `support-and-branding-final-build.log`,
`final-emulator-regression.log`, `decoder-emulator-regression.log`.
Машинні результати: `final-verification.json`, `artifact-verification.json`.

У збірці залишається повідомлення AGP/R8
`Class file resource provider does not support async parsing` для
`OrderedClassFileResourceProvider`. Це tooling fallback; build успішний.
Повідомлення не приховували, помилкою застосунку його не класифіковано.

## Артефакт для перевірки

`build/branding/UA-Cast-branding-debug-universal.apk` — ізольований debug universal,
applicationId `com.uacastplayer.debug`, версія 0.9.7 (164), **39 642 120 байтів**.
Він призначений для перевірки; це не опублікований release APK.

SHA-256: `23568c092b09e8d22c03149fb0231eed181c91f89845acd0043949ff277fef3f`.

Play AAB локально зібраний: **18 544 331 байт**.

## Межі висновку та залишок

Фізичні телефони та Xiaomi TV не використовуються за поточною вказівкою власника.
Вигляд перевірено native rendering-тестами та на емуляторі; фактичний launcher
Xiaomi TV і Hisense/DLNA цим прогоном не перевірялися.

Зміни цього оновлення локальні: нових commits, GitHub CI runs чи публічного
application release у цьому етапі не створено. Старий зелений CI не вважається
перевіркою нового коду. Поточну робочу гілку, звичайний Git index та сторонні зміни збережено.

Історичні NextLib/GPL releases залишаються неврегульованими; legal readiness gate
не відкривали. Успішні технічні перевірки цього оновлення не усувають цю перешкоду
для публічного application release.
