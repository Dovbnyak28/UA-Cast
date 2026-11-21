# Google Play Billing: увімкнення в Play-варіанті — 2026-11-21

## Що змінено

- `BuildConfig.PLAY_BILLING_LIVE`: `true` лише в build type `play` (`bundlePlay` / `assemblePlay`),
  `false` у `release` (GitHub APK) і `debug`. `PremiumAvailability.STORE_IS_LIVE` тепер дорівнює цьому
  прапору (compile-time константа — R8 прибирає невикористаний провайдер з кожного варіанта).
  Причина: Google Play бере оплату лише за збірку, яку сам встановив; тест ліцензованим тестувальником на
  треку неможливий, поки Play-збірка не містить живого провайдера.
- `PlayBillingProvider`: у `launchBillingFlow` передається `offerToken` тієї пропозиції
  (`oneTimePurchaseOfferDetails`), ціну якої показав каталог. Billing 8 дозволяє одному одноразовому
  продукту мати кілька варіантів покупки та знижки; без токена Play міг би обрати іншу пропозицію.
- Продукт: `premium_lifetime`, `inapp`, базова ціна **3,99 EUR** (задається лише в Play Console; застосунок
  показує `formattedPrice` від Play у валюті покупця). Чекліст тестувальника — `docs/RELEASING.md`.

## Що перевірено

| Перевірка | Результат |
| --- | --- |
| Компіляція debug / release / play, detekt | OK |
| Згенерований `PLAY_BILLING_LIVE` | debug `false`, release `false`, play `true` |
| Unit-тести (debug / release / play / core) | див. маркер у плані (повний прогін після патча) |
| Play-збірка (R8, підписана локальним debug-ключем) на емуляторі API 36 з Play Store, без акаунта Google | `BillingClient` з'єднується з Play Store (`Finsky: Billing preferred account via installer for com.uacastplayer`); Premium показує «Premium is currently unavailable in Google Play…» без кнопки з ціною; «Restore purchases» → «Could not reach Google Play…»; Premium не видано; FATAL 0 |

## Що неможливо перевірити звідси

Реальна оплата, підтвердження (acknowledge), відновлення й повернення коштів працюють лише для збірки,
завантаженої на трек Play Console і встановленої з Play акаунтом ліцензованого тестувальника. Для цього
власнику потрібно: активувати `premium_lifetime` з ціною 3,99 EUR, завантажити підписаний `bundlePlay` AAB на
internal testing, додати тестувальників у *License testing* і пройти чекліст з `docs/RELEASING.md`.

## Зауваження UX

Після «Restore purchases» повідомлення з результатом з'являється нижче видимої області й частково під нижньою
панеллю навігації — його видно лише після прокрутки. Варто прокручувати до нього або показувати snackbar.
