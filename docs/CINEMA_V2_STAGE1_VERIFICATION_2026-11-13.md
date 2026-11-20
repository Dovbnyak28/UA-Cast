# Cinema V2 — етап 1: контракти плеєра

## Межі

Виконано лише роботу з командами плеєра, MediaSession, власністю локального/Cast/DLNA-відтворення та регуляторами. Перехід до етапу 2 потребує окремого дозволу. Теми, Lite/Premium, залежності, Cast SDK adapter та DLNA transport у цьому diff не змінюються.

## Збереження робочого стану

Початкова гілка: `codex/release-0.9.6`; HEAD: `d6728a6632d8bde9bd035a561118a621059321d7`.
До змін було 342 unstaged tracked-файли, 575 untracked-файлів, 0 staged та 9 раніше видалених tracked-файлів. Ці зміни не скинуто й не включено автоматично в коміт.

Локальний checkpoint: `build/cinema-v2-stage1-2026-11-11/before/`. Архів містить усі 1597 наявних tracked/nonignored untracked-файлів (36 066 701 байт до стиснення); SHA-256 звірено з manifest. Окремо збережено Git patches, status та index. Дата каталогу відповідає початку роботи, дата цього звіту — її завершенню. Checkpoint залишається локальним build-артефактом.

## Контракти та виконання

`PlaybackCapabilities.kt` містить ефективні можливості та перетин owner policy з поточними командами Media3. `PlayerUiState` зберігає чинний API полів; вони є проєкціями спільного розрахунку, а не незалежними правилами.

| Стан | Play/Pause, Prepare | Seek | Next/Previous | Stop через MediaSession |
|---|---|---|---|---|
| Немає каналу | Ні | Ні | Ні | Ні |
| Локальний foreground / PiP | За командами engine | Non-live, seekable, за командою engine | За межами списку | Так |
| Локальний background без PiP | Ні | Ні | Ні | Ні |
| Chromecast або DLNA, включно з background | Ні для локального engine | Ні | За межами списку | Ні для локального engine |
| Обидва remote-прапорці | Локальне керування заблоковане до звільнення обох | Ні | За межами списку | Ні |
| Локальна fatal error | Ні | Ні | За межами списку | Так |

Next/Previous — команди списку застосунку: вони не залежать від наявності сусіда в singleton timeline Media3. Stop тут звільняє поточний запит через власника; він не завершує активну трансляцію закриттям телефонного UI.

Шляхи виконання:

- UI Play/Pause → `togglePlayback` → свіжі capabilities → поточний intent engine → Media3 → `PlayerEventListener` → атомарна UI-проєкція → оновлення команд SessionPlayer.
- Системні media controls → існуючий MediaSession → SessionPlayer → перевірка поточної policy та engine commands → той самий handler власника.
- UI Seek → PlayerNavigationController → свіжа policy + перевірка `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` → існуючий engine. Відносне перемотування читає актуальну позицію; чинний SeekPolicy збережено.
- Вибір каналу → чинний debounce 220 ms → PlayerSessionStateMachine → `applyChannelSwitch` → той самий engine та наявні Cast/DLNA adapters → спільна синхронізація команд. Пізній застарілий запит не замінює останній вибір.
- Background/foreground → негайне оновлення UI та MediaSession. Пауза користувача не скасовується поверненням у застосунок.

Новий runtime Player не створюється. PlayerEngineFactory та звільнення через наявний SessionPlayer залишаються власністю PlayerViewModel; fullscreen/inline продовжують використовувати той самий native view.

## Підтверджені виправлення

1. SessionPlayer більше не оголошує Play/Pause та Prepare, якщо engine не підтримує відповідну команду. Handler повторно перевіряє команду під час виконання.
2. UI одразу відкликає локальні playback/navigation-команди при background, навіть якщо engine вже був на паузі й не надіслав нового playback callback.
3. Відкликана engine-команда Seek не проходить через старий UI callback.
4. Кнопки та fullscreen-жести яскравості починаються від фактичного значення вікна. Window override має пріоритет над системним значенням; непотрібне читання Settings за наявності override усунено.
5. Відкрита панель рівнів читає фактичну гучність, включно зі змінами апаратними кнопками, та ефективну яскравість вікна/системи. Обидва значення оновлюються раз на 500 ms. Спостереження прив'язане до композиції та RESUMED lifecycle; після закриття панелі або втрати активності не працює.

## Перевірки

- Перший RED: 9 тестів, 8 assertion failures; контрольний virtual-next/previous тест пройшов.
- Наступний RED: підтверджено stale background UI, відкликаний Seek та два напрями brightness gesture. Дві інші невдачі були помилкою fixture: тест припускав початковий pause intent, хоча VM явно встановлює autoplay intent. Fixture тепер встановлює власний початковий стан; production заради цих assertions не змінювався.
- Проміжний GREEN: 250 тестів player/регуляторів, 0 failures/errors/skips.
- Mutation check: заміна `canPlay && engineCommand` на `canPlay || engineCommand` зламала 2 з 3 capability-тестів. Файл відновлено в `finally`, SHA-256 відновленого файлу підтверджено.
- Додатковий RED для відкритої панелі, що слідує за системною яскравістю: очікувано 0.8, фактично 0.2509804 після зміни системного значення. Виправлено спільним lifecycle-bound оновленням обох рівнів; тест збережено.
- Повний debug + screenshot-прогін перед остаточним форматуванням: 2956 тестів, 0 failures/errors/skips. Перша спільна команда завершилася на шести зауваженнях detekt; умову спрощено, рядки перенесено, пороги не змінювалися.
- Остаточна верифікація: **BUILD SUCCESSFUL**, 4m 42s. **2957 тестів у 519 suites, 0 failures, 0 errors, 0 skipped**, включно з Roborazzi screenshot verification. У diff додано 22 регресійні тести.

Виконана фінальна команда (локальний offline cache, JDK 21, один Gradle worker):

```text
./gradlew.bat :app:detekt :app:lintDebug :core:test :ffmpegdecoder:testDebugUnitTest :app:verifyRoborazziDebug :app:assembleDebug
```

`:app:testDebugUnitTest`, screenshot verification, app detekt, lint та packaging виконані на фінальних source-файлах. `:core:test` і `:ffmpegdecoder:testDebugUnitTest` у фінальній команді були **UP-TO-DATE**; вони не включені як нові виконані тести до числа 2957. `:core:detekt` пройшов через UP-TO-DATE у попередньому спільному gate. Окремі `check-architecture-boundaries.sh` і `check-detekt-baseline.sh` пройшли; app detekt report містить 0 findings, baseline не розширювався.

`lintDebug` на остаточній реалізації вже пройшов: 0 errors, 0 warnings, 2 informational hints (`OldTargetApi`, `GradleDependency` для Compose BOM). Target SDK та залежності в етапі 1 не змінювалися; їх оновлення потребує окремої перевірки сумісності. Початкове зауваження lint щодо створення тестового state всередині composition усунено перенесенням state за межі composition.

Логи й копії JUnit XML: `build/cinema-v2-stage1-2026-11-11/`.

Фінальні докази: `accepted-verification.log`, `accepted-debug-results/`, `final-test-summary.json`.

Зібрано universal debug APK: `app/build/outputs/apk/debug/app-universal-debug.apk`, 39 657 223 байти. SHA-256: `1EA01B3C52CD6CB7D2727C9DEF2D2186093FC2A138D6C1CB4BE9BED9E9D5C2FD`. Це тестова debug-збірка, а не підписаний release для розповсюдження.

Контрольна звірка початкового manifest: 1590 оригінальних файлів залишилися byte-identical, змінено лише 7 запланованих наявних файлів; неочікуваних змін/втрат немає. Додано 2 production-файли, 6 тестових файлів та цей звіт. HEAD та Git index збережено. Окремий фінальний checkpoint цих 16 файлів: `build/cinema-v2-stage1-2026-11-11/after/`.

## Перегляд змін за п'ятьма напрямами

- Коректність: SDK-команди перетинаються з owner policy; при dispatch не використовується застарілий UI snapshot. Віртуальні channel-команди відокремлені від seek усередині media item.
- Читабельність: один розрахунок policy/capabilities замість окремих умов у UI-проєкції та MediaSession; регулятори мають один короткий lifecycle-owned sampler.
- Архітектура: adapter не володіє ще одним engine; наявні власники MediaSession, Cast/DLNA та Compose state збережені. Production-import boundaries проходять перевірку.
- Безпека: нових permissions, exported components, endpoints, сховищ секретів або логування вмісту джерел не додано. Регулюється brightness вікна, а не глобальне системне налаштування; запис Settings у новій регресії є лише тестовим.
- Продуктивність: немає постійного фонового sampler; відкритий активний sheet виконує одну пару читань раз на 500 ms. Window override дозволяє уникати читання Settings. Перевірені next/previous index-функції мають O(1), без нового проходу всього плейлиста при Seek.

## Межі доказів і наступні перевірки

- Реальний телевізор, Chromecast/DLNA receiver та hardware media keys фізичного телефона у цьому етапі не використовувалися. Unit/Compose/MediaController перевірки не доводять сумісність конкретного receiver, зокрема Hisense VIDAA.
- Чинні remote adapters не надають Pause/Seek; цей етап не обіцяє нових receiver-можливостей і не надсилає локальні команди замість них.
- Release/Play APK, підписання, публікація, зовнішній CI та device instrumentation не є результатом цього локального debug-gate.
- Оформлення Cinema V2, приховування/розташування нових елементів, адаптивні екрани та окремий TV acceptance plan належать наступним погодженим етапам.
