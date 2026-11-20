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
- Фінальний Debug APK — 40 454 381 байтів; Play AAB — 19 157 480 байтів.
  15 native-бібліотек у кожному перевірені за завантажуваними ELF-секціями,
  6 ліцензійних assets і юридичні HTML відповідають джерелам.

## Перевірки

- Python scripts: **39/39**; у тому числі source/native/relink і legal guards.
- Kotlin/Java instrumentation після змін relink — компілюються.
- Privacy anchors і licence reader/UI — локальна регресія пройшла.
- Фінальна збірка Debug APK/Play AAB — **BUILD SUCCESSFUL**, 4m 03s після underlay-оптимізації.
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
  Контрольні P95: відкриття 40k — 22.69 ms, player — 104.06 ms, fullscreen — 114.82 ms,
  EPG — 31.33 ms. Зміна emulator backend не є app speedup; ліміти не збільшені.
- Під непрозорим expanded player головний scaffold тепер зберігає composition,
  навігацію та Android views, але не виконує приховане малювання. Видимість
  пов'язана з фактичним завершенням opening animation; при collapse/close
  малювання відновлюється. Request token не утримує попередній список каналів.
  Локальні тести перевіряють drawing, animation, native-view retention і перший
  opaque frame при disabled motion. Проміжний run 40402261527 дав повні вісім
  measurements, але player/fullscreen P95 133.91/191.11 ms перевищили 100 ms.
  Це не позначено як успіх або доведений frame-time speedup.
- Traces також показали повторний layout прихованої навігації, а тест довів,
  що її semantics усе ще позначалися видимими. Тепер covered subtree не
  вимірюється/не розміщується; composition, стан і native views зберігаються.
  Тест перевіряє невидимість semantics, нуль повторних child measures при
  зміні ширини та правильне відновлення layout після reveal. Фінальна цільова
  регресія: 39 Debug tests, 0 failures; detekt успішний.
- Fullscreen effect при зміні режиму виконував cleanup старого режиму перед
  застосуванням нового: тест отримав `[-1, 6]` замість `[6]` orientation requests.
  Cleanup тепер належить lifetime player/Activity; зміна режиму застосовує тільки
  цільове значення. Controller не надсилає повторну ту саму орієнтацію.
  Тест також перевіряє background recomposition, вихід із fullscreen і disposal.
- PR run 40402261539 впав на cancellation icon test, тоді як незалежний push
  run 40402252366 на тому самому SHA пройшов усі jobs. Це не відкинуто як flake:
  новий детермінований тест довів, що `Call.cancel()` може синхронно віддати
  header `IOException: Canceled` до continuation cancellation. Спільний HTTP
  helper тепер перевіряє owner cancellation і для header failure; скасування
  не отруює negative cache. Genuine header/body IOException зберігаються.
  Network/icon регресія: 143 Debug, 131 Release, 131 Play, нуль failures;
  cancellation-owner сценарій додатково повторюється 64 рази з ізоляцією fixtures.
- Коміт app-коду `23b2a3bc0bc76c65e7f663843b648152cd2ff107` пройшов
  [PR Android CI 40406555193](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40406555193)
  і push CI 40406549536: core 133, Debug/screenshot 2811, Release 2364, Play 2364;
  unit failures/skips — 0. API 24/30/36 app suites — 161/164/164, по 6
  assumptions, 0 failures. Decoder/relink API30 знову успішні.
- API35 run 40406555307 виявив завершені `DrawFrames` при FrameTimeline `dur=-1`.
  P95 fullscreen у наявних samples — 73.23 ms, але порожні ітерації інших cases
  роблять загальний run непридатним. Підтверджено офіційним source JAR AndroidX
  1.4.1: API31+ потребує completed actual/expected slices; API30 використовує
  стандартне basic UI/RenderThread matching. Первинні traces збережено.
- Ізольований [API30 probe 40409419686](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40409419686)
  виконав два незалежні повні eight-journey trials того самого app-коду. Дані
  повні в обох, але limits не пройдені: player 163.48/88.71 ms і fullscreen
  127.44/101.60 ms. Це не відкидається як випадково невдалий run. Основний gate
  тепер використовує API30 для надійного збору стандартного frame CPU metric;
  усі числові limits/iteration counts збігаються з попередніми. Validator також
  перевіряє `context.build.version.sdk` і не приймає іншу API чи empty iteration.
- Source/test analysis підтвердив зайву інфляцію PlayerView/TextureView при кожному
  inline/fullscreen переході. Movable content тепер зберігає один native view
  у межах phone screen. Тест реального PlayerScreen + PlayerViewModel перевірив
  5 циклів, ідентичність view/texture/player, зміну geometry і detach при закритті;
  до зміни тест падав. PiP і TV зберігають власні surface типи та ownership.
- Спільні viewport constraints визначаються перед вимірюванням шарів: вкладений
  PlayerZone constraints scope більше не відкладає opacity callback до моменту,
  коли navigation уже виміряна. Збережено точний поріг navigation rail та єдиний
  PlayerHost call site. UI/layout/motion/screenshots regression і detekt пройшли.
- Додано native instrumentation регресію з власним MP4: фактичний decode,
  pause, три fullscreen цикли зі збереженням PlayerView/TextureView, resume і cleanup.
  Внутрішній SurfaceTexture належить Android і може перестворюватися при detach;
  native regression перевіряє нові video frames та декодовані пікселі після resume.
  Вона компілюється; її виконання на API24/30/36 має підтвердити CI кандидата.
- CI artifacts тепер включають decoder unit та instrumentation reports разом
  із app/core reports. Новий platform/evidence validator має 41 passing Python test.
- Decoder buffer unit tests додані до Debug/Release CI та signed-release gate:
  локально 5/5 у кожній конфігурації, раніше CI запускав лише instrumentation
  цього модуля.
- GitHub показав фактичне Node20 deprecation warning. Перевірені офіційні
  signed commits `checkout` v7.0.1, `upload-artifact` v7.0.2 і
  `download-artifact` v8.0.2, `setup-java` v6.1.0 і Gradle Actions v6.4.0
  використовують Node24. Android/native/performance/
  signed workflows оновлені зі збереженням повного SHA pinning; YAML-моделі
  до/після відрізняються лише `uses` refs. Перевірка delivery має пройти CI.
  [Checkout release](https://github.com/actions/checkout/releases/tag/v7.0.1),
  [upload](https://github.com/actions/upload-artifact/releases/tag/v7.0.2),
  [download](https://github.com/actions/download-artifact/releases/tag/v8.0.2),
  [Java](https://github.com/actions/setup-java/releases/tag/v6.1.0),
  [Gradle](https://github.com/gradle/actions/releases/tag/v6.4.0).
- Локальний R8 повідомляє, що AGP class-file provider не підтримує async
  parsing. Це tooling warning; збірка завершена. Його не приховано suppression.
- Остаточні CI results потрібно звіряти із SHA останнього коміту
  [гілки PR №4](https://github.com/Dovbnyak28/UA-Cast/pull/4/checks).
  Старі невдалі або скасовані прогони не підтверджують стан наступних комітів.

## Контрольна стабілізація 27 жовтня

- Повний CI для `e3d4692` виявив надто сильну вимогу native тесту щодо identity
  SurfaceTexture на API 24/30/36 та забруднення спільного filesDir у п'яти Release
  snapshot-тестах стороннім `last_crash.txt`. Це не підтверджує production data loss.
  Snapshot fixtures тепер використовують ізольований Context/filesDir; додатковий
  тест перевіряє збереження сторонньої діагностики. Локальні Debug/Release/Play
  регресії успішні. Тестовий socket worker завершується перед наступним тестом.
- CastButton використовував синхронне завантаження SDK-модуля на Main. Тепер
  офіційний overload отримує application context і спільний IO executor;
  MediaRouteButton зберігає themed UI context. Тест падає до зміни і проходить
  після неї, перевіряючи actual executor thread та обидва типи context.
  Помилка async setup логуються без URL/облікових даних та захоплення Activity.
- Performance run [40414333582](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40414333582)
  містить усі вісім вимірів, але не пройшов бюджет: player P95 126.460 ms,
  fullscreen P95 100.484 ms при ліміті 100 ms. Startup/playlist/EPG/memory пройшли.
  Це залишається невдалим результатом; вплив async Cast setup потребує нового CI.
- Останні зміни проходять новий повний Android CI і performance gate у PR №4;
  результат попереднього SHA не переноситься на наступний. Пристрої не використано.
- Commit `3f862be` пройшов повні unit/screenshot suites: Debug 2814,
  Release 2365, Play 2365, core 133, decoder Debug/Release по 5; нуль failures/skips.
  Quality та packaging jobs також успішні. Native тест на API 24/30/36 підтвердив
  повторний video output, але перевірив зупинку одразу після click, до Compose
  disposal. Helper тепер чекає фактичного видалення mini-player та idle перед
  перевіркою зупинки й очищення media items. Measurement fixture задає фіксовану
  landscape геометрію; production sensor transitions перевіряє lifecycle suite.
  Ці test-only зміни потребують наступного повного CI; assertions не вилучено.
- Performance [40418231238, attempt 2](https://github.com/Dovbnyak28/UA-Cast/actions/runs/40418231238/attempts/2)
  отримав усі вісім вимірів: fullscreen P95 96.987 ms у бюджеті, player P95
  146.719 ms перевищує 100 ms; startup/playlist/EPG/heap у бюджеті. Async Cast
  усуває синхронний module setup, але цей прогін не доводить frame-time speedup.
  Перша спроба цього run не дійшла до build/tests через пошкоджений CMake ZIP;
  друга на тому самому SHA успішно зібрала native inputs і виміряла застосунок.

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
