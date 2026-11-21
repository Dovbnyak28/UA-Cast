# Стабілізація локального відео — 9 жовтня 2026

## Межі цього проходу

Перевірено доставку вибраного файла в UI, обробку пізніх помилок Media3
після переходу на віддалене відтворення та завершення файлових HTTP-відповідей.
Змінено чотири production-файли й чотири тестові файли. Це доповнення до
локальної, ще не опублікованої реалізації відеофайлів; не аудит кожного рядка
репозиторію і не підтвердження відсутності всіх багів.

Перед виправленнями додано три негативні регресійні тести. У прогоні з 15
випадків впали саме ці три; 12 попередніх контрольних випадків пройшли.
Збережено початкові XML-звіти та журнал у локальному ignored `build/`.

## Підтверджені проблеми та мінімальні виправлення

### 1. P2 — втрата повторного відкриття того самого файла

- Файли: `app/src/main/kotlin/com/uacastplayer/media/LocalVideoOpenViewModel.kt`
  і `app/src/main/kotlin/com/uacastplayer/ui/media/LocalVideoDocumentHost.kt`.
- Шлях: `open()` → однакові метадані в StateFlow → `LaunchedEffect`, ключем
  якого раніше було тільки відео → другий явний запит не запускає callback.
  Сценарій відтворюється, коли другий запит надходить до спостереження
  споживання першого. Це не вимога відкривати всі проміжні скасовані запити:
  семантика «останній вибір перемагає» збережена.
- Виправлення: наявна generation використовується як `requestId` результату
  і ключ ефекту. Підтвердження споживання перевіряє ID, а не рівність або
  посилальну ідентичність метаданих. Ефект зберігає завершений результат до
  callback, щоб повторний `open()` не змінив ID підтвердження.
- Тести: `twoExplicitOpensOfTheSameFileAreNotCollapsedIntoOneUiEvent`;
  посилений `consumingAnOldResultCannotEraseTheNewSelection` повторює ту
  саму URI та той самий об'єкт метаданих. Старе підтвердження не стирає нове.

### 2. P2 — пізня локальна помилка псує стан віддаленого відтворення

- Файл: `app/src/main/kotlin/com/uacastplayer/player/PlayerFailureHandler.kt`.
- Шлях: `PlayerEventListener.onPlayerError()` → handler; ViewModel уже
  позначив Chromecast або DLNA власником відтворення → гілка локального
  файла раніше все одно виставляла `fatalError` і змінювала контролери.
- Виправлення: локальні Media3-помилки ігноруються, коли remote вже володіє
  відтворенням. Помилки приймача залишаються відповідальністю Cast/DLNA
  callback, їх обробку це не відключає.
- Тест: `aLateLocalDecoderErrorDoesNotInvalidateHealthyRemotePlayback`.
  Перевіряє незмінність UI, відсутність retry/network/skip/control-ефектів
  і будь-якого звернення до локального engine. Це підтвердження політики
  callback тестом, не відтворена помилка конкретного телевізора.

### 3. P2 — помилка callback скасування заважає закриттю дескриптора

- Файл: `app/src/main/kotlin/com/uacastplayer/data/cast/LocalVideoResponseServing.kt`.
- Шлях: завершення GET або `stop()` → `LocalVideoRead.close()` →
  `CancellationSignal.cancel()` → exception стороннього callback →
  закриття прикріпленого handle раніше пропускалося.
- Виправлення: non-fatal помилка скасування ізольована від закриття handle;
  повторне закриття лишається ідемпотентним. Наявний non-fatal helper не
  приховує fatal-помилки JVM чи скасування coroutine.
- Тест: `aThrowingProviderCancellationCannotPreventDescriptorClosure`.
  Інжектує `IllegalStateException` у cancellation callback; перевіряє
  повну HTTP-відповідь, завершення сервера і рівно одне закриття handle.
  Це підтверджена fault-injection межа, не свідчення, що такий provider
  уже трапився на телефоні користувача.

## Перевірки

- Точковий прогін: **27/27**, нуль failures/errors/skips; включає сім
  незмінених live-window lifecycle сценаріїв. Detekt і lintDebug пройшли
  у тому самому прогоні за 2 хв 2 с.
- Дванадцять repository-перевірок пройшли: архітектурні межі, порожній
  Detekt baseline, документація, локалізація, кольори/glow, lifecycle,
  чутливі логи, Premium purity/gating, розміщення Cast іконки та ізоляція
  benchmark fixtures. Правила, baseline, goldens і бюджети не послаблені.
- Усі **56** scoped Android-файлів основного workspace і review checkout
  збігаються за SHA-256. Тести не стосуються сторонніх вебзмін workspace.
- Перший повний прогін завершального результату не має; він не зарахований
  як успішний. Повторний повний gate пройшов за **9 хв 30 с**: 227 завдань,
  29 виконано і 198 up-to-date. **Debug 2702, Release 2308, Play 2308**:
  нуль failures/errors/skips. Незмінні Core 133 результати використано повторно.
- **49 screenshot goldens незмінні**, доданих/змінених — нуль. Три записані
  діагностичні background previews під ignored build не є зміною еталонів.
  Lint Debug/Release/Play має нуль errors/warnings; по два інформаційні Hints
  щодо наявного target API 36 та версії Compose BOM. Detekt, debug/test APK
  і компіляція обох benchmark варіантів застосунку та harness пройшли.
- Компілятор виявив шість зайвих `!!` і невідповідну назву override-параметра
  у двох нових тестових файлах. Після повного gate це прибрано без зміни
  assertions; production-файли незмінні. Повторний gate пройшов за **6 хв 31 с**
  (122 завдання: 16 виконано, 106 up-to-date): повні **Debug 2702** і
  **Release 2308**, а також **23 вибрані Play-тести**; нуль failures/errors/skips.
  Повний Play-прогін 2308 вище виконаний перед цим суто тестовим очищенням.
  Компіляція unit-тестів усіх трьох варіантів тепер без Kotlin warnings.
  Lint Debug/Release/Play і Detekt остаточних файлів пройшли; ті самі два
  lint Hints залишилися, їх не приховано suppression.
- Окремо пройшли вісім тестів валідатора performance budgets та дванадцять
  контрактних тестів instrumented runner. Це перевірка інструментів, не
  нові вимірювання швидкодії чи memory peak.

## Mi A2: остаточна production-збірка

Android 11 / API 30, серійний номер `263efaf`. **21/21 native тест пройшов
за 90,551 с**: два LocalVideo, п'ятнадцять PlayerLifecycle та чотири
PlayerVideoFit. Перевірено реальні ContentResolver-дескриптори, HTTP
HEAD/range/close, Media3 playback/seek/release, збереження попереднього IPTV
ключа й суміжні lifecycle/remote-ownership/UI-fit сценарії.

Використано лише синтетичний короткий MP4 та окремий debug application ID.
Перед запуском збережено приватний стан debug-пакета, після — відновлено й
підтверджено SHA-256 трьох оригінальних файлів. Основна release-версія та її
дані не змінювались. Тести трьох нових fault-injection меж виконувалися на
host; ці native результати є суміжною регресією, а не відтворенням усіх
трьох інжектованих збоїв на фізичному телефоні.

Локальні ignored журнали: `build/local-video-followup-*.log` та
`app/build/device-audit/instrumented-cdca271647c84d2dae479b2a2283bb9c.txt`.
Приватний recovery-архів до публічного звіту не включається.

## Межі висновку

Справжнє приймання файлів Chromecast/Mi TV/Hisense VIDAA та матриця
контейнерів/кодеків потребують окремої перевірки на приймачах. Цей прохід
не додає транскодування і не гарантує «всі формати». Раніше виміряні
перевищення frame-time бюджетів не оголошені виправленими.

Випуск, зміна версії, публікація GitHub і вимкнення комп'ютера в цьому
проході не виконуються. Приватні recovery-архіви й device-логи не
призначені для публікації.
