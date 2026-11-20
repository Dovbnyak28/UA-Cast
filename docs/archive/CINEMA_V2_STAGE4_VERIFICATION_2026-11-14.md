# Cinema V2 — етап 4: плеєр, EPG та керування

Етап 4 завершено 14.11.2026. Нижче наведено фактичні локальні перевірки, артефакти та межі підтвердження. Етап 5 не розпочато.

## Реалізований інтерфейс

Повноекранний плеєр використовує компактний заголовок каналу, читабельні scrim-панелі поверх відео та нижню округлену Cinema-панель. У ній розділено поточну телепередачу, часові мітки й неінтерактивний EPG progress, транспорт і швидкий телегід. Прев’ю наступних каналів залишено для стану без поточної передачі; повний каталог залишається в меню. На вузьких телефонах другорядні дії розміщені окремо від транспорту й переносяться за потреби; у короткому landscape використано компактний рядок. Активний таймер зберігає доступний підпис часу й у компактному режимі.

Прямий EPG відкриває чинну шторку без запуску іншого каналу. У fullscreen доступний чинний стан підключеного приймача зі Stop; стандартний Cast picker і DLNA chooser, налаштування звуку/субтитрів, пропорції кадру, таймер, PiP та відновлення після помилки збережені. Пункт якості відкриває наявне інформаційне вікно виміряних характеристик: окремого вибору video rendition чинна модель не надає.

EPG має заголовок каналу, дату й зону, поточну передачу з виділенням, початок–завершення, прогрес і наступні передачі. «Зараз» повертає до поточної передачі після перегляду попередніх; повернення до сьогодні доступне окремою дією. У короткому landscape компактний заголовок залишає місце тексту програми; при великому шрифті вузькі рядки складаються вертикально. Loading, error, відсутнє джерело/збіг та порожній вибраний день мають різні повідомлення й чинний retry там, де він застосовний.

Обидва progress — декоративний EPG та справжній playback timeline — мають різні semantics. Лише доступний Seek із відомою тривалістю отримує Slider і ±10 секунд. Невідома тривалість зберігає достовірний минулий час. Наявна політика етапу 1 забороняє Seek для live, включно з live-вікнами; цей UI не відкриває DVR всупереч цій політиці.

Cinema, Azure, Midnight, Daylight і System збережені. Чинний UaPlayerTheme залишає відеокерування темним у Daylight, щоб забезпечити контраст поверх відео. Перехід теми не змінює provider topology відеоповерхні. Нових blur-ефектів або постійних анімацій не додано; чинний LIVE-feedback підкоряється reduced-motion політиці етапу 2.

## Поведінка керування

| Стан | Поведінка |
|---|---|
| Фактичне відтворення, без взаємодії | 3 с на телефоні; 8 с на TV; враховується Android accessibility timeout |
| Пауза, підготовка/buffering, ended або помилка | Таймер не приховує потрібне керування; відновлення/вихід доступні |
| Відкрита EPG/меню, натиснута кнопка, активний жест рівня | Телефонний таймер утримується; закриття дає новий повний інтервал |
| TV з відкритим діалогом / touch exploration | Автоприховування призупинене |
| TV: перше OK на прихованих контролях | Лише показ; жодного одночасного Pause |
| Background / disposal | Lifecycle-owned delay скасовано; старий hide не доставляється після повернення |
| Непідтверджений Play/Pause/Seek | Контекстна кнопка прихована; чинні handlers і MediaSession guards збережені |

## Межі та збереження стану

До змін прочитано звіти етапів 1–3 і затверджений план від 11.11.2026. Збережено й SHA-256 перевірено 1666 наявних файлів, Git patch/status/index та дев’ять початкових відсутніх шляхів у `build/cinema-v2-stage4-2026-11-14/before/`. Гілка `codex/release-0.9.6`; початковий HEAD `d6728a6632d8bde9bd035a561118a621059321d7`.

Engine, MediaSession, контракти можливостей, Cast/DLNA adapters, джерела й завантаження EPG, Lite/Premium та дизайн-система тем не змінювалися. Єдина зміна арифметики таймлайну знаходиться в UI-проєкції FilePlaybackTimeline: elapsed time не обнуляється через невідому duration. Той самий ViewModel, Media3 Player, VideoSurface та movable-content шлях inline/fullscreen збережені.

Чинна fullscreen-політика SENSOR_LANDSCAPE збережена. Перевірено portrait-компонування контролів та inline-шлях; нової опції portrait-lock цей етап не додає. Системні регулятори гучності/яскравості, апаратні медіакнопки та MediaSession з етапу 1 залишені з тими самими handlers.

## Фактично виконані перевірки

| Перевірка | Етап 4 | Порівняння з етапом 3 |
|---|---|---|
| app debug, включно з Compose | 3 132; 0 failures/errors/skipped | 3 099 → 3 132 (+33) |
| app release | 2 476; 0 failures/errors/skipped | 2 475 → 2 476 |
| app Play | 2 476; 0 failures/errors/skipped | 2 475 → 2 476 |
| core | 133, успішно | Без зміни; фінальний gate перевірив UP-TO-DATE результати |
| ffmpegdecoder JVM debug/release | По 5, успішно | Без зміни; результати UP-TO-DATE |
| Roborazzi | 115 PNG; окремий verify успішний | 102 → 115; +13 нових Cinema Player/EPG кадрів |
| Native app, Android 16/API36 | 229 унікальних: 223 pass, 6 assumptions; 237.224 с | 227 → 229; без нових пропусків |
| Native software decoder | 15 pass; 7.583 с | Той самий чинний декодер |
| Cinema native UI | 2 сценарії × 4 профілі, усі pass | 320dp/100%, 320dp/200%, landscape/100% і /200% |
| Однаковий controls audit до/після | По 3 сценарії, pass | Незмінений тест; 640 × 320dp |
| lint debug/release/Play | 0 errors, 0 warnings; по 2 hints | Попередні OldTargetApi / Compose BOM |
| detekt app/core | Pass, baseline порожній | Правила, пороги й виключення збережені |
| Python CI/security/performance-budget script tests | 46 pass | Скриптові перевірки, не runtime FPS gate |
| Shell contract suites | 33 pass | Baseline 8, doc refs 5, instrumented runner 12, Cast placement 8 |
| Архітектура, документація, baseline, Cast placement | Pass | Захищені production-шари перевірено за SHA-256 |
| Debug / release / Play | APK успішні; bundlePlay AAB успішний | Нічого не опубліковано |
| Benchmark / non-minified app та harness | Обидва варіанти скомпільовані; fixture isolation pass | Runtime Macrobenchmark не запускався |

Record і verify — різні завершені запуски. У таблиці наведена кількість унікальних кейсів у кожному app variant, а не сума повторних прогонів. UI-категорія RequiresComposeTestManifest виконується у debug; чинні release/Play-фільтри не змінено. Шість assumptions: фізичний LAN probe, TV playback acceptance, два ручні temporary-playlist probes та два Mi TV document-picker сценарії.

33 нові debug-кейси: TV interaction — 4, UI capabilities/command layout — 7, lifecycle auto-hide — 3, EPG interaction/window geometry — 5, unknown duration — 1, Cinema screenshot — 13. Чинний EpgGuideTimeZoneTest має 6 успішних JVM-кейсів, EpgTimeZoneInstrumentedTest — 2 успішні Android-кейси; точні часові діапазони перевірено після зміни presentation.

Докази: `build/cinema-v2-stage4-2026-11-14/final-record.log`, `final-verify-shipping.log`, `accepted-native-final.log`, `full-app-instrumented.log`, `decoder-instrumented.log`, `test-results-summary.json` та `final-results/`. Окремі EPG/timezone, capability, lifecycle, MediaSession та playback regressions входять до повних suites. D-pad/OK/Back перевірені UI-тестами й Android emulator; це не фізична сертифікація TV-пульта.

У логах toolchain є повідомлення про SDK XML v4 при parser v3 та R8 ClassFileResourceProvider/async parsing. Збірки й quality gates завершилися успішно; показник «0 warnings» у таблиці стосується саме lint. AGP/R8/SDK tooling не оновлювалися в цьому UI-етапі; ці повідомлення залишено для окремого планового оновлення toolchain.

Під час попереднього повного JVM-прогону один ProxyManifestLeaseTest завершився socket-close timeout на Windows: усі 172 байти вже отримані, сервер має activeClients=0 та вільні workers. Незмінені чотири ізольовані proxy-тести й обидва фінальні повні debug-прогони пройшли з тим самим 5000 мс лімітом. Діагностичний XML збережено; це не заявляється виправленим production proxy-багом. Жоден тест не вимкнено і ліміт не збільшено.

Початковий native Back-збій локалізовано до системного ImmersiveModeConfirmation, який перехоплював фокус. Підтвердження вимкнено лише на власному емуляторі; production BackHandler не змінено. Два старі EPG selectors оновлено з одного часу на точний start–end range. Одну помилку експорту PNG (run-as не має права писати у зовнішній каталог) виправлено в локальному helper через читання власного cache/diagnostics; це не збій відтворення.

## Universal debug APK

[Завантажити UA-Cast-0.9.7-Cinema-V2-stage4-universal-debug.apk](../../build/cinema-v2-stage4-2026-11-14/artifacts/UA-Cast-0.9.7-Cinema-V2-stage4-universal-debug.apk).

- applicationId `com.uacastplayer.debug`, versionName `0.9.7`, universal versionCode `164`.
- ABI: `armeabi-v7a`, `arm64-v8a`, `x86_64`; розмір 39,657,567 байтів.
- SHA-256: `335C6C5A43A8A85E5DBEE04E972CBE436E56214856CF33CD2A0C7A5A063435DA`.
- SHA-256 тотожний APK, установленому для фінального native-прогону. ZIP/ABI/metadata та нові Cinema-класи в DEX перевірено. Це debug-артефакт для тестування, не опублікований Play-реліз.

## Перелік змін і контроль збереження

[Повний перелік 74 змінених/нових файлів](CINEMA_V2_STAGE4_CHANGED_FILES_2026-11-14.md), з них 16 production Kotlin-файлів та 41 PNG (21 Roborazzi-еталон у diff і 20 Android-доказів/контактних аркушів). Зміни пораховано від контрольної копії перед етапом 4, без зарахування попередньої роботи.

Усі 1666 початкових файлів, дев’ять попередніх видалень, Git HEAD та index збережені. Нові production-зміни обмежені ui/player та ui/epg; чотири локалізації мають новий напис «Зараз». Stage3 Home/каталог, VideoSurface/PlayerHost, engine/remote repositories, Lite/Premium, залежності й правила якості не змінено. Локальний архів із SHA-256: `build/cinema-v2-stage4-2026-11-14/after/`.

## Виявлені проблеми й регресійний захист

- Приховане TV OK виконувало togglePlayback; actual UI/VM тест спочатку відтворив небажаний Pause, потім перевіряє показ і окрему наступну команду.
- TV приховував панель за wantsToPlay під час buffering. Таймер тепер використовує actual isPlaying.
- Непідтримувані Pause/Seek залишалися в UI. Нові тести перевіряють відсутність кнопок та скасування scrub при відкликанні Seek.
- Seekable HTTP VOD не отримував уже наявний таймлайн; тепер він використовує ту саму проєкцію та handler, що й файл, за чинним canSeek.
- Невідома тривалість обнуляла показ elapsed time; тест відтворив 5000 → 0 перед виправленням.
- EPG не мав прямого повернення до поточної передачі, часу завершення та read-only progress semantics; збережені три незалежні регресії.
- Native-огляд показав ламання DLNA-підпису посеред слова при 200%; адаптивна іконка має той самий доступний опис.
- Короткий landscape/200% залишав надто мало EPG viewport. Посилений geometry-тест перевіряє область читання, а не лише часткову видимість тексту.
- Активний countdown при 320dp/200% стискав останню кнопку нижче 48dp. Регресійний тест відтворив проблему; FlowRow переносить другорядні дії, зберігаючи підпис 60:00 та мінімальні зони натискання. Native-тест перевіряє геометрію цього стану окремо.
- Фінальний lint відхилив використання screenHeightDp у новому compact EPG. Окремий тест спочатку відтворив втрату reading viewport, коли вікно має 320dp, а legacy Configuration повідомляє 400dp. EPG тепер бере LocalWindowInfo.containerSize, переводить px у dp поточною density та адаптує заголовок до вікна; lint-правило збережене.

## Порівняння з затвердженим Cinema V2

| Частина | Відповідність / практичне уточнення |
|---|---|
| Верхня панель | Назва й route/actions над відео; довгі назви скорочуються з повним semantics-підписом |
| Нижня панель | Семантичний золотий акцент Cinema, транспорт, EPG та віконні дії; без blur і нового playback owner |
| Live progress | Поточна передача та її час; без ручки й SetProgress |
| Файл/VOD | Справжній таймлайн за поточним canSeek та duration; непідтримувані дії відсутні |
| 320dp/200% | Розділені/компактні рядки, іконка DLNA, адаптивні EPG header/rows; розмір тексту не фіксується поверх системного масштабу |
| Теми | Чинні п’ять палітр, контрастний темний player у Daylight, збережені токени етапу 2 |

Макети містять художній фон; Android-докази використовують синтетичні канали й чорний фон fixture. Це реальні production-компоненти на Android, а не доказ декодування саме зображеного відеоконтенту. Декодування й власність engine перевіряються окремими regression suites.

## Реальні Android-скриншоти

[Огляд Player та EPG](../design/cinema-v2-stage4/android-overview.png), [до/після на однаковому 640 × 320dp](../design/cinema-v2-stage4/before-after-controls.png).

- Player: [landscape](../design/cinema-v2-stage4/actual-player-landscape.png), [320dp](../design/cinema-v2-stage4/actual-player-320.png), [320dp/200%](../design/cinema-v2-stage4/actual-player-200.png), [активний таймер/200%](../design/cinema-v2-stage4/actual-player-timer-200.png).
- EPG: [320dp](../design/cinema-v2-stage4/actual-epg-320.png), [200%](../design/cinema-v2-stage4/actual-epg-200.png), [короткий landscape/200%](../design/cinema-v2-stage4/actual-epg-landscape-200.png), [наступний день](../design/cinema-v2-stage4/actual-epg-next-day-200.png), [повернення до player](../design/cinema-v2-stage4/actual-player-after-epg.png).
- Теми: Cinema показана в основних кадрах; також збережені [Azure](../design/cinema-v2-stage4/actual-player-azure.png), [Midnight](../design/cinema-v2-stage4/actual-player-midnight.png), [Daylight](../design/cinema-v2-stage4/actual-player-daylight.png), [System](../design/cinema-v2-stage4/actual-player-system.png).
- [Локальний відеофайл у player](../design/cinema-v2-stage4/actual-local-video.png): скриншот із чинного LocalVideoInstrumentedTest після seek у паузі, без меню каналів. На цьому кадрі видно проміжний buffering-стан; сам PNG не доводить декодування відеокадру. Успішне відтворення синтетичного файлу чинним engine перевіряють окремі assertions цього native-набору.

18 оригінальних Android PNG не перемальовано. Два оглядові зображення лише компонують їх із підписами. Before/after використовує незмінений PlayerControlsAuditInstrumentedTest на однаковому viewport, а не різні розміри телефона. Нові Roborazzi-еталони перевіряють production-компоненти окремо від цих native-скриншотів.

## Продуктивність і межі вимірювання

У чотирьох прийнятих профілях виконано по 30 циклів приховування/показу панелі. Наведений час включає синхронізацію Compose test rule та обидві операції; це не час кадру, не latency окремого tap і не FPS-бенчмарк.

| Android viewport / fontScale | Цикли | Медіана / p90, мс | PSS fixture, КБ |
|---|---:|---:|---:|
| 320 × 640dp / 1.0 | 30 | 136 / 152 | 174 729 |
| 320 × 640dp / 2.0 | 30 | 135 / 163 | 181 762 |
| 640 × 360dp / 1.0 | 30 | 149 / 166 | 175 676 |
| 640 × 320dp / 2.0 | 30 | 149 / 151 | 175 935 |

Сирі дані: `build/cinema-v2-stage4-2026-11-14/android/accepted-*/cinema-v2-stage4/presentation-measurement.txt`; враховані лише чотири відповідні профілі з перевіреними width/fontScale. У каталозі додаткового controls-аудиту можуть бути також раніше збережені файли кешу — вони не зараховуються як нові вимірювання цього профілю.

На однаковому 640 × 320dp незмінений controls-аудит зафіксував PSS 171 512 → 177 228 КБ при 100% та 168 669 → 174 840 КБ при 200% з таймером. Це одиничні знімки процесу з test harness, без декодера; вони не доводять ані витоку, ані покращення peak heap. Окремого порівнянного FPS-бенчмарку в етапі 3 не було. Тому кількісний висновок про швидкість на фізичному слабкому телефоні залишається відкритим.

Новий UI не створює Player, MediaSession чи власного EPG ticker. Programme lookup кешується за channel/data/now, час оновлюється чинним EPG state. Таймер приховування має lifecycle/composition ownership, а ScheduleList використовує lazy-список; нових постійних анімацій, blur або callbacks у довгоживучих об’єктах немає. Hash-перевірка захищених шарів і регресійні тести доповнюють ці статичні докази, але не замінюють runtime Macrobenchmark.

## Офіційні джерела та версії

Використано наявні Media3 1.10.1, Compose BOM 2026.06.01, Lifecycle 2.10.0, Kotlin 2.4.0 та AGP 8.13.2. Залежності не оновлювалися.

- [Media3 events та actual isPlaying](https://developer.android.com/media/media3/exoplayer/listening-to-player-events) — намір відтворювати не є фактом відтворення.
- [Player command availability](https://developer.android.com/reference/androidx/media3/common/Player#isCommandAvailable(int)) — UI споживає чинні ефективні capabilities, handlers етапу 1 збережені.
- [Compose effects](https://developer.android.com/develop/ui/compose/side-effects#launchedeffect) — cancellation та ключі UI-таймера; rememberUpdatedState для актуального callback.
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) — мінімум 48dp для інтерактивних дій.
- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) — read-only EPG progress відокремлений від Seek.
- [TV navigation](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv) — передбачувані D-pad/Back та фокус.
- [Compose FlowRow](https://developer.android.com/develop/ui/compose/layouts/flow) — перенесення дій при нестачі ширини без стискання інтерактивних цілей.
- [WindowInfo.containerSize](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/WindowInfo) — розмір поточного Compose-вікна для адаптивного EPG.

## Що залишається непідтвердженим

Фізичні Chromecast, DLNA/Hisense VIDAA, Android TV, апаратні медіаклавіші телефона та матриця всіх Android-версій цим емуляторним прогоном не сертифікуються. Runtime Macrobenchmark на еталонному API30 та фізичному слабкому пристрої потрібен для числового вердикту FPS/heap; UI-цикли й PSS fixture його не замінюють. Remote SDK/transport та live-timeshift policy не розширювалися.

Віддалений GitHub CI, публікація й етап 5 до цієї роботи не входять.
