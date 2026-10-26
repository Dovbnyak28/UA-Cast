# Основа майбутньої реклами у Lite — 2026-10-03

## Що реалізовано зараз

Підготовлено два незалежні банерні місця та межу для майбутнього рекламного
провайдера. **Реального рекламного SDK, запитів, трекінгу й рекламних ID зараз немає.**
У production використовується `BannerAdIntegration.Disabled`: список увімкнених
місць порожній, дозвіл на запити — `UNKNOWN`. Порожніх рекламних рамок не показуємо.
Плейлист, плеєр, покупки, версію, залежності та manifest permissions не змінено.

| Місце | Розташування | Коли допускається |
| --- | --- | --- |
| `HOME_BANNER` | Після основної дії, персональних карток і dashboard на «Огляді» | Є канали; користувач — Lite; усі дозволи виконані |
| `CHANNELS_BANNER` | Окремий footer під списком, не overlay поверх каналів | Є канали; користувач — Lite; усі дозволи виконані |
| Плеєр, fullscreen, mini-player, Cast/DLNA-відтворення | Рекламних місць немає; відкритий `playerRequest` блокує також банери під ним | Не допускається |
| Android TV, пульт, стартова підказка, порожній/перший loading-екран | Рекламних місць немає або запити блокуються | Не допускається |

Lite не означає автоматичний дозвіл на мережевий запит. Для показу необхідні
одночасно: актуальний unpaid entitlement, явно увімкнений placement,
`AdRequestPermission.ALLOWED`, незаблокований екран, phone mode та lifecycle `RESUMED`.
Відсутній entitlement, `UNKNOWN`/`DENIED`, Premium та background блокують renderer.

## Premium: один власник прав, без нового платного пакета

`MainAppContent` передає вже наявний поточний `Entitlements` у `LocalBannerAdAudience`.
Не створено дубльований preference на кшталт `adsRemoved`, окремий товар чи нову
гілку перевірки Billing. `effectiveTier.isPaid` — той самий розв'язаний доступ, що
використовується іншими можливостями програми. Усі активні legacy paid записи
обробляються існуючою міграцією; прострочений запис вже не є Premium.

`AppViewModel.init` синхронно викликає `PremiumRepository.loadInitial()` до показу
`MainAppContent`. Тому збережений Premium не проходить через Lite-рекламу на першому
кадрі. Недоступний магазин не скасовує збережену покупку: ця поведінка репозиторію
не змінена. Успішна покупка прибирає існуючий банер через recomposition; підтверджений
refund повертає Lite через той самий entitlement stream, без повторної навігації.

На paywall не додано продаж ще не підключеної рекламної можливості. Коли реальна
реклама запрацює, її відсутність у Premium потрібно описати у відповідному UI.

## Власність і життєвий цикл

- `ads/BannerAdPolicy.kt` — чисті правила, без Android, мережі та UI.
- `ui/ads/BannerAdIntegration.kt` — vendor-independent composable renderer і вимкнений
  default. Від нього не залежить Billing/Data/Media3.
- `ui/ads/BannerAdSlot.kt` — gate перед renderer та lifecycle collector. Вимкнена
  реклама взагалі не створює цей collector. Він отримує PAUSE/STOP, а не тримає
  останній `RESUMED` через lifecycle-gated збір Flow.
- Заміна renderer/placement, покупка, відкритий player, відкликання дозволу,
  background та видалення екрана прибирають його composable subtree. Майбутній
  адаптер повинен скасувати роботу й звільнити SDK view через `onDispose`/structured
  cancellation. Сам контракт не може замінити правильну реалізацію vendor SDK.
- `BannerAdFrame` використовується тільки для завантаженого банера. Він має
  локалізовану позначку EN/UK/RU/ES, центрування та cap 640dp, зокрема коли зовнішній
  modifier вимагає всю ширину. Pending/no-fill/error не мають навіть порожньої рамки.
- Для локальних файлів Channels content отримав `weight(1f)`, як і наявний
  URL/PullToRefresh branch. Footer не витісняється за межі екрана; контент не перекритий.

## Як підключати реальну рекламу наступним етапом

1. Вибрати провайдера, формат, placement IDs та схему приватності. Поточна основа
   не обирає їх автоматично і не реалізує CMP/таргетування.
2. Реалізувати `BannerAdRenderer` окремим UI-adapter. Не починати SDK/network роботу
   в singleton, constructor чи `Application.onCreate` до gate. Не зберігати Activity
   у довгоживучому провайдері. Запит/AndroidView мають належати активній composition.
3. Передати `BannerAdIntegration` через `LocalBannerAdIntegration` над `MainAppContent`
   у телефонному root. Конфігурацію публікувати як нові незмінні snapshots із Flow
   або Compose state; не мутувати `enabledPlacements` in-place.
4. Увімкнути лише allowlisted placements і встановити request permission після
   належного permission/privacy flow. `ALLOWED` означає право на запити, **не** згоду
   на персоналізовану рекламу. SDK-specific privacy signals — відповідальність adapter.
5. Pending/no-fill/error: не викликати `BannerAdFrame`. Не додавати нескінченний retry,
   запит на кожну recomposition або preload поза gate. Помилки SDK не мають ламати
   канал, навігацію або плеєр. Валідувати SDK-specific banner dimensions.
6. Перевірити актуальні правила обраного провайдера та магазину, privacy policy,
   disclosures і потрібні manifest permissions перед увімкненням. Цей етап їх не
   підтверджує, бо SDK й рекламна обробка даних ще відсутні.
7. Повторити regression gate та device QA з test IDs, включно з покупкою/відновленням
   Premium offline, permission revocation, background/rotation, no-fill, network loss,
   320dp/200% font, navigation/mini-player і TV. Production IDs активувати окремо.

## Автоматизовані перевірки

Нові тести: 8 pure policy + 11 slot lifecycle/state + 8 actual-screen integration.
Перевіряються: cached Premium, paid legacy tiers, expiry, live purchase/refund,
permission revocation, playback block, PAUSE/STOP/RESUME, renderer replacement,
видалення екрана, TV, default-off, no-fill, порожній стан, обидва джерела плейлиста,
вибір точного каналу без дубльованого callback та cap/центрування на широкому екрані.

Два нові golden images містять **лише test-source fake renderer**, явно позначений
«Тестовий банер · не справжня реклама». Не додано production placeholder і не
перезаписано попередні goldens. Знімки Home/Channels оглянуто візуально.

Початковий screen-тест використав запит `Second`, однаковий із назвою каналу:
matcher клікав текст search field ще до завершення асинхронного пошуку. Це помилка
тесту, не доказ поломки вибору каналу. Запит змінено на `sec`, очікування — саме
`Second` result, assertions виконуються після UI idle. Усі 8 screen-тестів пройшли.

## Фінальний локальний gate

Після останнього source/test виправлення: **BUILD SUCCESSFUL, 9m 39s**, 203 tasks.
Запущено `assembleDebug`, `assembleDebugAndroidTest`, `detekt`, `core:test`,
`core:detekt`, `verifyRoborazziDebug`, `testReleaseUnitTest`, `testPlayUnitTest`,
`lintDebug`, `lintRelease`, `lintPlay`, `assembleRelease` з `uacast.requireSigning=true`,
offline, одним worker та без daemon. Наявні незакомічені зміни не відкинуто.

| Перевірка | Результат |
| --- | --- |
| Debug, включно з усіма 27 новими ad-тестами | 2 507 tests; 0 failures/errors/skips |
| Release unit | 2 182 tests; 0 failures/errors/skips |
| Play unit | 2 182 tests; 0 failures/errors/skips |
| Core unit | 126 tests; 0 failures/errors/skips; unchanged/cache-valid |
| Roborazzi | 47 unchanged; 0 changed/added/recorded у фінальному verify |
| App/core Detekt | Успішно; app report порожній; baseline не розширено |
| Debug/Release/Play lint | 0 errors, 0 warnings; наявний `OldTargetApi` Hint у кожному |
| 15 `scripts/check-*.sh` | Усі пройшли |
| `git diff --check` | Пройшов; окремі Git CRLF notices не є compilation errors |
| Підписаний universal release APK | Зібраний; signature v2/v3 перевірена |

R8 повторив два наявні build-tool notices про unsupported async parsing
`OrderedClassFileResourceProvider`. Не приховано їх suppressions і не змінено SDK
лише заради усунення повідомлення. Нових lint warnings/errors немає.

Актуальний локальний APK: `app/build/outputs/apk/release/app-universal-release.apk`.
Версія залишена **0.9.7 / 164**, minSdk 24, targetSdk 36; ABI `armeabi-v7a`,
`arm64-v8a`, `x86_64`. Розмір 24 178 412 bytes.

- APK SHA-256: `20AA9AF8FE67034D56EA16C6B247C68CF0BD0E3608F8A1D40D01C53EA6701AC8`.
- Certificate SHA-256: `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e`.

Нову збірку цього етапу не встановлено на пристрої й не опубліковано. Device QA
справжнього рекламного SDK/монетизації залишається окремим обов'язковим етапом після
вибору та інтеграції провайдера. Наявність hooks не означає, що справжні покази вже
протестовано. Наразі готові й перевірені лише вимкнена-by-default основа та gate.
