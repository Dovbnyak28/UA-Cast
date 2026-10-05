# UI та полегшення плеєра — 4 жовтня 2026

## Межі змін

Практичне полірування поточної дизайн-системи, не новий редизайн або сертифікація всіх
зовнішніх потоків. Не змінені версія, залежності, Premium, протоколи Cast/DLNA,
власник Media3, порядок attach-new/detach-old та обов'язкові performance budgets.
Сторонні незакомічені файли основного checkout збережені.

## Підтверджені виправлення

| Місце | Проблема | Виправлення |
| --- | --- | --- |
| Media3 PlayerView | `use_controller=false` лише ховав повністю створену native-панель, хоча керування вже реалізоване в Compose | Власний video-only content layout без controller placeholder; TextureView inline/fullscreen/TV і SurfaceView PiP залишені |
| Фон Azure/Cinema | Два декоративні glow-шари малювалися після `drawContent`, тонуючи текст, кнопки й непрозорий вміст | Glow намальовано перед вмістом; палітра й статичний фон збережені |
| Entry-анімації | Неанімовані, повторно показані та вже анімовані рядки утримували graphics layer | Ранній static path і видалення тимчасового шару після завершення; motion policy спільна для контейнера каналів |
| Пошук усередині групи | Немає доступної кнопки очищення; порожній результат не пропонує явної наступної дії | Clear у полі та окрема доступна дія у scrollable порожньому стані |
| Заголовок груп | Незважена текстова колонка могла витісняти Layout за великого шрифту | Зарезервована ширина дії; перевірено 320dp / 200% та реальне переключення вигляду |
| Тестовий runner | Свіжий debug-пакет без `shared_prefs` зупиняв підготовку тестів | Зберігаються тільки наявні папки; початкова відсутність теж відновлюється; fixture-папки переміщуються для діагностики, не видаляються |

Не додано bitmap-іконок, мережевих запитів, blur, нескінченних анімацій або нових
scope/executor. Lazy-списки залишаються віртуалізованими. Для lazy placement застосовується
та сама motion policy, що й для entry-ефекту; ручний drag обраного не змінений.

## Докази до виправлень

Нове assertion на відсутність native controller падало на старому layout. Обидві
перевірки очищення пошуку падали до додавання дій. Чотири layer assertions виявили
старий зайвий graphics layer. Після виправлення тестового захвату два pixel assertions
виявили tint на непрозорому білому foreground у Azure/Cinema; Midnight пройшов.
Непідтримуваний `captureToImage` і відсутній record-output не названі багами програми.

Повторне вимкнення/ввімкнення motion перевіряється окремо через presentation policy.
Цей сценарій пройшов, тому не доданий до списку підтверджених дефектів.

## Перевірки

Цей перший набір перевірок стосується змін, опублікованих у `33e8dfe`.

- Цільові host assertions, detekt і debug/test APK пройшли перед повним gate.
- На власному API-36.1 емуляторі пройшли 29 native-сценаріїв: Media3 layout/30 handoffs,
  пошук, lifecycle, fit-mode та player controls за звичайного й великого шрифту.
- Native hierarchy: **41 View → 10 View**. Це 31 вилучений View, не вимір економії MB
  або гарантія певного FPS. Subtitle/artwork/image/overlay bindings залишаються доступними.
- Debug files відновлені й перевірені за SHA-256; початкова відсутність shared_prefs
  окремо підтверджена після завершення. Основний пакет фізичного пристрою не змінений.
- Оновлені тільки 14 навмисно змінених/нових goldens. Знімки та два додаткові ad-fixture
  diff переглянуті; функціональні ad/Premium assertions не послаблені.
- Перший повний gate виявив саме два неоновлені ad-fixture goldens. Після огляду вони
  записані точково; цей червоний прогін не використовується як фінальний зелений результат.
- Фінальний повний gate в ізольованому review checkout пройшов за 10 хвилин:
  Debug 2 608, Release 2 268, Play 2 268, Core 133; нуль failures/errors/skips.
  Це 7 277 виконань у різних варіантах, не 7 277 унікальних сценаріїв.
  Усі 49 screenshot goldens перевірені; App/Core detekt, три lint-варіанти та
  debug/application-test APK assembly пройшли. Lint не має errors/warnings;
  наявний один Hint у кожному варіанті не видається за нуль усіх діагностик.
- Повторна native-регресія фінального debug APK: 29/29, 95.787 секунди.
  Контрольні знімки fullscreen 100%/200% та surface-only додатково переглянуті;
  це fixture UI без реального декодування, а не вимір відео-FPS чи peak heap.
- 11 repository/static checks, вісім performance-validator tests і п'ять
  CI-runner contract/privacy tests пройшли. Detekt baseline не збільшений.

## Повторний прохід: форма паку логотипів

Підтверджені ще два практичні дефекти:

- `IconSourcesSection` стирала чернетку одразу після виклику `onAddSource`, хоча
  `CustomIconSettingsController` міг відхилити неправильну адресу, дублікат або
  перевищення ліміту; feature gate також міг не виконати дію. Сім із восьми нових
  assertions впали на попередньому коді. Тепер чернетка очищається тільки після
  появи відповідного канонічного джерела в authoritative списку. Інша адреса,
  вже наявний пакет або запізніле підтвердження не стирають новіше введення.
- У режимі TV стрілка Up залишала фокус у полі URL. Окремий keyboard assertion
  відтворив це до зміни. Додано чинний `tvTextFieldNavigation` та видимий фокус
  Add/Remove; зайвого focus target не створено.

Не змінені controller/repository, правила валідації, ліміти, Premium або мережеві
запити. Acknowledgement належить тільки часу життя форми; жодного нового фонового
scope або постійної роботи не додано. Поле успішного додавання перевіряється саме
через EditableText: placeholder у семантичному Text не названий дефектом програми.

Усі дев'ять нових host-сценаріїв пройшли. Повний ізольований gate — 11m12s:
Debug 2 617, Release/Play по 2 268, Core 133; усі 49 goldens, lint, App/Core detekt
та debug/test APK assembly пройшли. Нуль failures/errors/skips у host XML;
Core результат повторно використаний Gradle для незміненого Core. Lint має
тільки один наявний Hint у кожному варіанті. 11 статичних checks також пройшли.
Нова native-регресія URL/TV/backup/player на власному API-36.1 емуляторі:
20/20 за 40.87s, включно з усіма чотирма новими URL-сценаріями. Відновлення
оригінального debug-файла підтверджене SHA-256. Фізичні пристрої не змінені.

Віддалений CI для попереднього `33e8dfe` має успішні unit/goldens,
quality/architecture, packaging та API 30/36 (по 147 native-тестів). Два інші
результати **червоні**, а не незавершені:

- API 24 досяг 30-хвилинного timeout у `UiPolishInstrumentedTest` після двох
  завершених методів. Cipher/provider та попередні player-controls тести пройшли.
  Старий журнал із крапками не визначає наступний метод чи причину зависання;
  цей випадок не названий production ANR або доведеною проблемою інфраструктури.
- API-35 performance run завершився вісьмома timeout очікування fixture-статусу.
  Жодного повного measurement JSON немає, тому нові FPS/heap/time висновки з нього
  не робляться. Це збій підготовки тестів, не вимір перевищення бюджету.

У runner додано raw start/end назви методів і запис partial log до завершення
процесу. Шість mocked runner contract/privacy cases пройшли; strict failure/OK
перевірки залишені. Benchmark driver перевіряє результат запуску Activity й при
timeout повідомляє screen/foreground та тільки синтетичні status-токени, без
повного приватного window dump. Harness зібраний; budgets/timeouts не послаблені.

Той самий `UiPolishInstrumentedTest` локально пройшов 8/8 за 76.272s на API 36.1,
із підтвердженим відновленням original debug-файла. Локальний fixture також показує
`benchmark-fixture-ready`. Це звужує пошук, але не доводить виправлення двох CI-збоїв
і не видається за перевірку на API 24/35 або фізичному TV.

Для опублікованого follow-up `4e272e2` усі шість Android CI jobs пройшли. Native:
API 24 — 148 тестів / 173.331s, API 30 — 151 / 174.663s, API 36 — 151 / 191.143s.
Raw звіти перевірені; попереднє API-24 зависання цього разу не повторилось. Це
успішний повтор, не доказ знайденої причини чи виправлення давнього зависання.

Локальний повний API-36.1 benchmark завершив native 8/8 за 589.047s; JSON містить
усі вісім journeys і повні iteration counts. Gradle task при цьому червоний через
`Failed to receive the UTP test results` / gRPC transport error, тому весь прогін
не названий зеленим gate. Незмінний validator також повернув failure:

| Локальний API 36.1 / software GPU | Значення | Поріг API-35 CI для порівняння |
| --- | ---: | ---: |
| Cold / warm display median | 2453.212 / 1811.446ms | 5000 / 2500ms |
| Restore 40k channels display median | 2108.806ms | 10000ms |
| Open channels / first player frame CPU P95 | 461.190 / 366.447ms | 100 / 100ms |
| Fullscreen / EPG guide frame CPU P95 | 128.506 / 142.463ms | 100 / 100ms |
| 350k EPG parse/index median | 3830.566ms | 60000ms |
| Worst EPG MemoryUsageMetric managed heap | 45689 KiB | 262144 KiB |

API/host/rendering різні, тому ці числа не видаються за контрольований before/after,
за результат нового API-35 CI або за показники фізичного телевізора. Наявність усіх
JSON measurements не скасовує UTP failure та чотири перевищення frame-порогів.

## Завершений API-35 вимір та стабілізація підготовки — 5 жовтня

На `4e272e2` віддалений API-35 run завершив 8/8 за 267.225s, нуль
failures/errors/skips; завантажені XML/JSON перевірені незалежно. Підготовка цього
разу не зависла. Сам performance gate **червоний** через три фактичні перевищення:

| Поточний API-35 CI | Значення | Поріг |
| --- | ---: | ---: |
| Open channels frame CPU P95 | 165.651ms | 100ms |
| First player frame CPU P95 | 241.873ms | 100ms |
| Fullscreen frame CPU P95 | 306.657ms | 100ms |
| EPG guide frame CPU P95 | 39.632ms | 100ms |
| Cold / warm display median | 1104.510 / 580.765ms | 5000 / 2500ms |
| Restore 40k channels display median | 1102.636ms | 10000ms |
| 350k EPG parse/index median | 1273.679ms | 60000ms |
| Worst EPG MemoryUsageMetric managed heap | 124524 KiB | 262144 KiB |

Числа нижчі за попередній повний CI run, але hosted runners і навантаження можуть
відрізнятися. Це не контрольований A/B і не доказ відсотка прискорення від конкретної
зміни. Startup/restore/EPG/memory правила пройшли, три frame правила залишаються відкриті.

В одній fullscreen trace поточного CI найдовший main-thread `postAndWait` — 214.629ms:
210.872ms sleep, 3.745ms scheduler-ready, 0.012ms running. RenderThread drawing до
230.620ms, buffer dequeue 157.434ms, GPU-completion wait 151.975ms. Layout до 5.070ms,
inflate 0.382ms, recomposition до 63.993ms. Це evidence конкретної trace про значну
частку renderer/synchronization wait; воно не виправдовує зміну state machine або
підняття бюджету й не визначає швидкість hardware GPU фізичного TV.

Окремий негативний тест підтвердив ще один дефект **тестової підготовки**, не програми:
зі сплячим дисплеєм `prepareFixture` викликається до `measureRepeated`, а wake-up
усередині AndroidX Macrobenchmark відбувається запізно. До виправлення тест впав
за 190.509s: `screenOn=false`, `foreground=null`, `visibleStatus=[]`.

Тепер driver будить дисплей і закриває тільки незахищений keyguard перед fixture.
PIN/налаштування блокування не змінюються. Новий regression відмовляється виконувати
sleep/fixture на фізичному пристрої. Після зміни readiness + cold/warm startup
пройшли 3/3 за 168.608s, з незмінними 10 ітераціями кожного startup. Обидва варіанти
harness компілюються; сам production APK після URL/UI gate не змінений.

До performance workflow додано цей regression поряд із тими самими вісьмома
обов'язковими measurement journeys. Жоден бюджет/timeout/minIterations не піднятий.
Стан дисплея старого червоного `33e8dfe` не збережений, тому цей підтверджений шлях
зависання не видається за доведену єдину причину того CI run.

Повтор на `b3df89c` завершений: усі шість Android CI jobs зелені. Performance
execution пройшов 9/9 за 257.36s (вісім journeys + sleeping-display readiness),
але незмінний budget gate знову відхилив три P95: канали 161.814ms, перший плеєр
234.486ms, fullscreen 310.594ms. Повні XML/JSON перевірені незалежно. Ці числа
не є виміром наступної production-зміни opening motion; деталі й межі нового
проходу: `docs/PLAYER_MOTION_STABILIZATION_2026-10-05.md`.

На `21c3be3` усі шість Android CI jobs пройшли, включно з API 24/30/36.
Performance run цього head перервався на ADB EOF / `emulator-5554 not found`:
лише три XML records замість дев'яти, фінального benchmark JSON немає.
Незмінний validator незалежно відхилив артефакти як неповні. Це не вимір
прискорення й не підтверджений crash production-коду; три попередні frame
перевищення залишаються відкритими до повного повтору.

Наступний вузький integration regression підтвердив повтор opening-анімації
при розгортанні мініплеєра: remember усередині expanded branch втрачав стан
після collapse. Modifier тепер створюється в стабільному PlayerHost, а
застосовується тільки до PlayerScreen. Чотири host-тести перевіряють десять
переходів, перерване відкриття, новий ключ та повне закриття/відкриття;
це не зміна Media3 lifecycle чи доказ роботи IPTV на фізичному TV.

П'ять UI-регресій також підтвердили стару програму EPG після зміни метаданих
за тієї самої URL: список каналів (tvg-id/tvg-name/назва), Home continue-watching
(tvg-name зі стабільною FavoriteKey) та мініплеєр. Три remember тепер залежать
від M3uChannel, а не лише streamUrl; рівні значення й далі використовують кеш.
Усі п'ять тестів падали до зміни та пройшли після неї. Деталі:
`docs/EPG_METADATA_REFRESH_STABILIZATION_2026-10-05.md`.

Фінальний ізольований прогін обох виправлень успішний (10m16s): Debug 2639,
Release/Play 2270 кожен, нуль падінь/пропусків, 49 незмінних golden comparisons,
lint/detekt, APK та benchmark Kotlin variants. Завершені Debug/golden результати
того самого коду й незмінні Core 133 повторно використані; попередній перерваний
прогін не зараховано як успішний. Також пройшли 12 repository checks, 8 validator
і 6 runner contract/privacy tests. Це локальна регресія, не новий вимір FPS і не
підтвердження роботи реального IPTV на TV.

## Що не підтверджено

Mi TV під час цього проходу недоступний; старі успішні TV-тести не видаються за тести
нового APK. Потрібен повтор на доступному реальному телевізорі. Native handoff/lifecycle
fixtures не доводять тривале декодування реального IPTV, роботу всіх кодеків або Hisense.

Попередній повний API-35 performance run на `352e386` завершив 8/8 journeys, але три
frame CPU P95 перевищували 100ms: канали 291.538ms, перший плеєр 355.126ms,
fullscreen 469.306ms. Повторний вимір поточного APK наведений вище;
старі числа не є показниками нового APK. Budgets не підняті й software-GPU затримки
не приховані. Попередній неповторений API-24 process crash також не має доведеної причини.

PR залишається draft; нового release або автоматичного merge цей етап не виконує.
