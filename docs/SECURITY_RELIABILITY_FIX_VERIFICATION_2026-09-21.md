# Перевірка виправлень аудиту — 21.09.2026

Усі підтверджені проблеми з аудиту `SECURITY_RELIABILITY_AUDIT_2026-09-21.md`, які можна було безпечно виправити в поточному робочому дереві, закриті.

## Закриті Android-проблеми

- XMLTV: обмежено довжину ID/icon-атрибутів, сукупний бюджет метаданих і розмір розпакованого XML; додано best-effort JAXP `maxAttributeSize` до SAX parser.
- DLNA SSDP: відхиляються порти `0`/поза діапазоном, `LOCATION` прив'язується до адреси UDP-відправника, помилковий endpoint ізолюється в межах одного пристрою, вимкнено HTTP redirects і додано загальний timeout опису пристрою.
- Proxy remux: elapsed-час draining обчислюється через monotonic `System.nanoTime()`.
- Remux initial wait і SSDP discovery deadline також переведені з wall clock на monotonic clock.
- SAF playlist/backup read і backup export закривають provider stream під час cancellation, тому
  завислий DocumentsProvider не утримує IO worker після виходу зі screen.
- Compose: Xtream password і parental-control PIN більше не зберігаються через `rememberSaveable`,
  тому секрети не потрапляють у Activity saved-state Bundle під час rotation/process recreation.
- Playlist/EPG controllers: кожне завантаження має monotonic generation; скасований запит більше
  не може опублікувати застарілий snapshot після швидкого перемикання джерела, виходу з add-flow
  або refresh. Generation перевіряється і після CPU/cache-стадії, перед callbacks та UI state.
- Startup playlist migration: повільне читання/копіювання legacy snapshot тепер перевіряє
  generation після кожної IO-стадії, тому новий add/switch не може бути перезаписаний старим
  startup state.

## Закриті веб-проблеми

- Firebase: асинхронні операції прив'язані до UID/session generation; перемикання акаунта очищує локальний стан без неявного cross-account upload; збереження використовує Firestore transaction + revision conflict.
- Node REST: суворо перевіряються `If-Match`/`baseVersion`, а malformed `Host`/request target більше не може завершити async HTTP handler.

## Перевірки

- `:app:testDebugUnitTest` — успішно.
- `:app:testReleaseUnitTest` — успішно.
- `:app:detekt` — успішно.
- `:app:lintDebug` — успішно.
- Node server tests — 2/2 успішно.
- `node --check firebase-auth.js` — успішно.
- `node --check diary-server.mjs` — успішно.
- SAF blocking read/write cancellation regressions — успішно.
- `git diff --check` — без помилок форматування.
- `:app:compileDebugKotlin` — успішно після UI security fix.
- Повторна перевірка після generation guards: `:app:detekt`, `:app:testDebugUnitTest`,
  `:app:lintDebug` — успішно.

Не замінено лабораторними тестами: живий Hisense VIDAA/DLNA стенд, фізичні телефони, Firebase Emulator/production rules, інструментальні тести на всіх API та зовнішній SCA/CVE-скан залежностей.
