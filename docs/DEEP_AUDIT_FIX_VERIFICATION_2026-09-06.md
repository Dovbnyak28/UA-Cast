# Виправлення глибокого аудиту — 2026-09-06

Це follow-up до `DEEP_AUDIT_2026-09-06.md`, а не переписування історичних результатів.
Обсяг: F1–F7, вимкнений Billing-дефект L1 і зауваження щодо локальних controls під час casting.
Особисті плейлисти не використовувались; усі нові hostile inputs синтетичні.

## Матриця виправлень

| Пункт | Що змінено | Регресійне покриття |
|---|---|---|
| F1 / P1 | `M3uEpgUrls` сканує optional EPG metadata без eager split/map/filter; максимум 16 distinct HTTP(S) URL, кожен до 4096 символів; cancellation під час сканування | `M3uEpgBudgetTest`: 3,5 млн entries, спільний бюджет aliases, invalid/Unicode/oversized URL, cancellation; окремий JVM 128 MiB запуск; ART на Samsung |
| F2 / P1 | `ProxyManifestResources` захищає видані manifest URLs від звичайного LRU. Зберігає reachable master/audio/video/key resources, current + previous revisions, один draining channel. Увесь rewrite або commit, або HTTP 503 без частково виданих URL | `ProxyManifestLeaseTest`: реальний loopback HTTP fetch root + 1000 segment URLs, rolling windows, master/audio/video, channel switch, late callback, stop, over-budget |
| F3 / P1 | `PlaylistSnapshotStore.save` повертає результат запису. Невдала migration copy не повертає source для destructive commit. Controller показує storage error; legacy FILE залишається і наступний запуск може повторити міграцію | `PlaylistMigrationFailureTest`: blocked `.new`, storage error, legacy retained, fault removed → recovery; `AtomicFileIoTest`: open/write/sync/rename failure, cancellation, replacement; Android real-filesystem fault injection |
| F4 / P1 | XMLTV aliases накопичуються в ordered set без `List.plus`; 16 на канал, 100000 на документ, 2 Mi символів загалом. Truncation доходить до EPG UI; старий v2 cache читається з тим самим бюджетом; index construction перевіряє cancellation | `XmlTvAliasBudgetTest`, додатковий `EpgSnapshotCodecTest`; 32000 repeated aliases на JVM 128 MiB і Samsung ART |
| F5 / P2 | Sleep expiry спочатку фіксує stop intent, скидає deferred foreground/local resume і recovery jobs, зупиняє local engine, Cast та DLNA. Late handback не відновлює local playback; явний Play/Retry/channel start може почати нове відтворення | `PlayerSleepIntentTest`: background expiry, synchronous/late handback, explicit restart, deferred resume; новий Android lifecycle regression |
| F6 / P2 | `PlaybackActivity` агрегує local publisher і окремі remote-owner leases. Application-owned `PlayerCastAdapter` продовжує спостерігати Cast/DLNA після clearing PlayerViewModel | `PlaybackActivityOwnershipTest`: remote переживає local publisher, незалежне звільнення двох owners; наявні lifecycle/state tests |
| F7 / P2 | Admission має абсолютний monotonic deadline від accept, включно з очікуванням у черзі. Drip bytes більше не продовжують 15-секундний бюджет безмежно; після headers повертається нормальний stream timeout | `ProxyAdmissionDeadlineTest`: чотири slow clients утримують всі admission workers, сервер закриває їх за deadline, наступний healthy request отримує 200, sockets звільнені |
| L1 / неактивний P1 | Billing purchases моделюються як Unknown (`null`) або authoritative snapshot. Failed/partial query не означає Known empty; refresh серіалізовано, purchase revision відкидає застарілий query | `BillingPurchaseSnapshotsTest`, додатковий `PremiumRepositoryTest`: cached lifetime не відкликається на першому query failure, але справжній порожній snapshot може перевести в Free |
| Remote UI | Під час Cast/DLNA приховано локальні Audio/Subtitles/Quality/Aspect/phone-volume дії; відкриті локальні dialogs закриваються, меню пояснює target. Guide/timer/channels залишаються | `PlayerRemoteActionsTest`, `PlayerRemoteControlsLayoutTest`: Audio зникає/повертається зі зміною target; Samsung UI suite |

Нові повідомлення storage error та remote controls доступні в усіх чотирьох локалізаціях.
Формат EPG cache не змінено; нові metadata limits не означають видалення програм передач.

## Додатковий дефект, знайдений перевіркою власних виправлень F2

Ліміту distinct resource IDs недостатньо. HLS header + 1 млн повторень `a\n`
має 2 000 032 bytes, але старий rewrite генерував 98 000 032 символи локальних URL.
Це відтворено на compiled production classes; приблизно 2,7 с на цьому ПК.

Тепер до eager line split перевіряється максимум 100000 рядків. Окремо обмежено
20000 URI-підстановок, включно з дублікатами, і консервативний output budget 8 Mi символів.
Budget перевіряється до додавання підстановки, а не після побудови великого результату.
П'ять `ProxyManifestAmplificationTest` покривають URI flood, blank flood,
single-line duplicate attributes, точну допустиму межу та великий replacement.
Відмова має залишити обидві попередні manifest windows незмінними.

Protected graph має максимум 20000 entries / 4 Mi retained URL/header/type characters;
окремо зберігається один bounded draining graph. Це не точний heap measurement:
map/object overhead і звичайний LRU існують додатково. Rewrite ще виконується під monitor;
обмеження роботи не є обіцянкою миттєвого cancellation або повністю неблокувального Main.

## Перевірки та відтворюваність

Фінальний host gate після додаткового HLS fix: **BUILD SUCCESSFUL, 1 хв 49 с**.
**2016 app + 114 core = 2130 tests**, failures/errors/skipped = 0; 346 suites.
Порівняно з попереднім аудитом додано 37 host test cases; існуючі screenshot goldens
в цьому проході не перезаписувались. Roborazzi, Detekt app/core, debug APK/test APK
та Play AAB пройшли. `lintPlay`: 0 errors, 0 warnings, 3 informational hints
(target/dependency update suggestions, не доказ production-помилки).
Два попередні повідомлення R8 про async parsing class resource provider залишаються;
приглушення цих build-tool messages або неперевірене оновлення залежностей не виконувалось.
Усі 9 repository guard scripts і `git diff --check` пройшли; Detekt baseline = 0.
Три CRLF notices не є whitespace errors.

Команда gate:

```powershell
.\gradlew.bat :core:test :app:verifyRoborazziDebug :core:detekt :app:detekt :app:lintPlay :app:assembleDebug :app:assembleDebugAndroidTest :app:bundlePlay --continue --offline --no-daemon --console=plain
```

JDK 21.0.11, Gradle 8.14.4; журнал — `build/deep-audit-fixes-gate.log`.
Окрема перевірка compiled M3U/XMLTV/proxy через JShell `-R-Xmx128m`
підтвердила `Runtime.maxMemory() = 134217728`: M3U (7000060 символів) і 32000 XMLTV aliases
оброблено, HLS із 1 млн повторень (2000032 символи) відхилено до першої URI-підстановки.
Журнал — `build/deep-audit-memory128.log`. Це лише isolated harness, не весь Android app у 128 MiB.

Під час fault-injection tests виявлена різниця Windows `File.renameTo` та Android rename:
Windows не замінює наявний файл. Дві host suites використовують вузький
`AndroidAtomicRenameShadow` лише для цього syscall seam; реальні start/sync/close/rollback лишаються.
Йому потрібен public no-arg constructor Robolectric, тому додано один документований test-only
`UtilityClassWithPublicConstructor` suppression, без нових baseline entries.
Production fallback, який би видаляв старий файл перед rename, не додано.
Android storage regressions окремо перевіряють реальну реалізацію без shadow.

## Samsung: що фактично виконано

Samsung **SM-M156B, Android 16 / API 36**, системний font scale **2.0**.
Встановлено тільки окремі `com.uacastplayer.debug` і `.debug.test` після дозволу користувача.
Runner зберіг і відновив debug files/shared_prefs; release package не змінювався.

Перший прогін: **35 сценаріїв, 34 pass, один failed assertion у новому sleep test**.
Expiry, paused intent і STATE_IDLE assertions пройшли. Впав наступний Play:
порожній synthetic HLS уже був у fatal-error стані, де production правильно вимагає Retry.
Fixture уточнено: timer expires одразу після нового prepare з перевіреним `fatalError=false`.
Це не підстава приховувати перше падіння або називати весь device suite зеленим.
Журнал: `app/build/device-audit/instrumented-7055dc046af747f2a0a6fafdc05873e4.txt`.

Пройшли real-ART M3U/XMLTV, atomic open/commit failure injection,
13 інших player lifecycle scenarios, video fit 4, UI polish 6,
empty playlist 1, fullscreen controls 3, parser correctness/performance 4.

| Synthetic input | Час | Process allocated bytes |
|---|---:|---:|
| 3,5 млн optional M3U entries | 1105 мс | 14098432 |
| 32000 однакових XMLTV aliases | 357 мс | 4170464 |

Input construction виключено; ART max heap тут 256 MiB. Це не peak/retained heap,
не окремі GC pauses і не release Macrobenchmark. Метрики одиничного debug sample.
Синтетичні screenshots/measurements: `shots/deep-audit-fixes-2026-09-06/samsung/`.
Переглянуто fullscreen 200% із timer, levels sheet, device picker та settings target:
ключові controls не накладаються один на одного, sheet actions доступні; title/preview ellipsis очікувані.
Чорне відео у fixtures не є тестом декодування — тестовий HLS не містить video frames.

Перед повтором SM-M156B від'єднано. ADB бачить **SM-J330F, API 28**;
запитано дозвіл встановити debug на інший телефон. До відповіді його стан лише прочитано.
Повтор device suite, виправленого sleep regression і новий HLS ART case **ще не виконано**.
Приватні recovery tar archives не слід включати до CI/GitHub artifacts.

## Post-change architecture check

- Нові parser budgets живуть у parser helpers; metadata normalization/limits не переміщені до UI.
- Protected HLS graph належить proxy registry; session stop очищає ownership. Це необхідна
  заміна неправильного LRU-контракту, а не новий загальний cache framework.
- Absolute socket deadline належить admission adapter; response serving має попередній timeout.
- Storage layer повідомляє успіх/помилку commit; migration orchestrator вирішує, коли retire legacy.
  `writeSafely` явно перевіряє sync і rename postcondition та не поглинає CancellationException.
- Sleep intent належить наявному PlayerViewModel orchestration. UI тільки надсилає команди.
  PlayerViewModel залишається великим: це не повне перепроєктування його state machine.
- Application-owned remote observer має process lifetime і не утримує Activity.
  Local і remote contributions мають різних явних owners; aggregate state не залежить від UI collector.
- Billing snapshot/revision/mutex мають одного власника; Play SDK objects не передаються в UI/domain.
- Не додано нових global singletons, dependency cycles або production suppressions.
  Є новий application-lifetime scope, обґрунтований F6; це не ViewModel/UI scope leak.
- Політики й failure cases мають незалежні tests. Широкий попередній dirty worktree збережено;
  цей звіт не приписує всі наявні зміни лише поточному проходу.

## Межі фінального вердикту

**Вердикт щодо коду: F1–F7/L1 і remote-capability UI зауваження реалізовано з регресіями;
локальний release gate пройшов. Остаточний device sign-off ще відкритий.**

**WOULD YOU RELEASE THIS BUILD to 100000 users: NO, поки що.**
Причина не в падінні фінального host gate: він зелений. Немає завершеного повтору
device suite після останніх змін, а реальні receiver/Play інтеграції ще не підтверджені.

До широкого релізу не можна стверджувати «всі можливі баги виправлено».
Окремо лишаються реальні Chromecast/Hisense VIDAA (кодеки, receiver rejection, reconnect,
stop delivery), Play Console/Billing end-to-end, тривалий live playback та memory/performance
профіль повного Android процесу на слабких пристроях. Це не замінюється fake SDK або host tests.
Продажі не ввімкнено, залежності/версію не підвищено, GitHub/Play публікацію не виконано.
Комп'ютер не вимикався.
