# Глибокий аудит UA CAST — 2026-09-06

Оновлення: це історичний read-only аудит до виправлень. Поточний стан F1–F7/L1,
регресійні результати й межі device-перевірки наведено в
[окремому follow-up звіті](DEEP_AUDIT_FIX_VERIFICATION_2026-09-06.md).

## Висновок

**До широкого релізу поточний стан не рекомендую.** Знайдено чотири P1, дві функціональні P2 та один обґрунтований умовний P2-ризик доступності локального proxy. Окремо є дефект у вимкненому зараз Play Billing шляху, який потрібно закрити до ввімкнення продажів.

P0 не підтверджено. Це не означає, що застосунок повністю перевірений або всі можливі помилки знайдено.

Production-код і тести в цьому аудиті **не змінювалися**. Додано лише цей звіт; діагностика створювала звичайні build/JVM outputs. Наявні зміни робочого дерева збережено.

| ID | Пріоритет | Проблема | Рівень підтвердження |
| --- | --- | --- | --- |
| F1 | P1 | M3U-метадані в межах ліміту файла спричиняють OOM | Відтворено на JVM, heap 128 MiB |
| F2 | P1 | Proxy витісняє ще потрібні ресурси довгого HLS-плейлиста | Витіснення відтворено; HTTP 404 простежено в коді |
| F3 | P1 | Міграція видаляє legacy-плейлист після невдалого запису його копії | Повний шлях у коді; I/O fault injection не запускався |
| F4 | P1 | XMLTV display-name спричиняють квадратичні алокації | Виміряно на JVM |
| F5 | P2 | Sleep timer не зупиняє receiver і не скасовує відкладене local resume | Повний шлях у коді; нової device-перевірки немає |
| F6 | P2 | Глобальна ознака playback переживає свого publisher і блокує prefetch | Повний шлях власників/колекторів у коді |
| F7 | P2 | Повільні LAN-клієнти можуть зайняти весь admission pool proxy | Обґрунтований умовний ризик; атака не запускалася |
| L1 | Неактивний; P1 перед продажами | Помилка першого Billing query може скинути кешовану платну ліцензію | Шлях у коді, але STORE_IS_LIVE = false |

Пріоритети: P0 — негайний блокер; P1 — виправити перед широким релізом; P2 — можна тимчасово прийняти лише з усвідомленими обмеженнями; P3 — підтримка/cleanup. Оцінка враховує наслідок і умови запуску, а не тільки кількість рядків чи рекомендації лінтера.

## Обсяг і метод

Інвентаризовано 459 production Kotlin-файлів у app і core. Поглиблено простежено наскрізні шляхи імпорту/відновлення плейлистів, M3U/XMLTV, локального плеєра, Cast/DLNA, proxy, іконок, кешів, Premium та пов'язаних UI/lifecycle-компонентів. Перевірено напрямки залежностей і merged Play manifest.

Це широкий статичний аудит із прицільними виконуваними перевірками, **не доказ ручного перегляду кожного рядка всіх файлів**, не повний security pentest і не новий прохід кожної кнопки на телефоні.

Для F1/F2/F4 використано поточні скомпільовані production-класи в ізольованому JShell/JVM. Вхідні дані синтетичні; особисті плейлисти не передавались стороннім сервісам. Android ART, декодер телевізора і реальна мережа цими експериментами не емулюються.

## Підтверджені виконанням дефекти

### F1 — P1 / High: розгортання EPG-списку M3U вичерпує heap

**Файл / функція:** [M3uParser.kt:129](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/playlist/M3uParser.kt:129>), parseEpgUrls; критичний eager split/map/filter у рядку 133.

**Шлях:** PlaylistFileLoader або PlaylistUrlLoader → обмежене читання файла → PlaylistRepository → M3uParser.parse → ParseState.acceptHeader → M3uAttributeReader → parseEpgUrls.

**Тригер:** заголовок EXT M3U з url-tvg, що містить 3 500 000 непорожніх значень «a,»; після нього звичайний один канал. Сумарний розмір ASCII-входу — **7 000 060 байтів**, менше [ліміту 8 MiB](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/playlist/PlaylistUrlLoader.kt:84>). Посилання на EPG можуть бути некоректні: саме тому їх слід безпечно відхиляти, а не створювати мільйони проміжних об'єктів.

**Очікується:** обмежений обсяг metadata, зрозуміле відхилення/скорочення надлишкових EPG URL, збереження працездатності застосунку.

**Фактично:** за heap 134 217 728 байтів отримано OutOfMemoryError: Java heap space. Стек: String.substring → Kotlin split → M3uParser.parseEpgUrls, рядок 133 → M3uAttributeReader.forEach → acceptHeader → parse. Одночасно створюються списки та мільйони підрядків; byte cap файла не є memory budget результату парсингу.

**Контроль хибного спрацювання:** схожий заголовок із 7 000 000 тільки ком без непорожніх елементів пройшов. Висновок не стосується будь-якого довгого заголовка. Попереднє виправлення квадратичного regex залишається правильним: новий дефект — у споживачі результатів лінійного scanner.

**Наслідок:** падіння процесу під час імпорту недовіреного плейлиста на пристрої з обмеженим heap. На Mi A2 цей конкретний OOM зараз не відтворювався.

**Виправлення:** ліміти кількості/довжини/загального обсягу EPG URL; рання валідація; інкрементальний розбір із cancellation. Самого splitToSequence недостатньо, якщо потім зберігати мільйони значень. Не лікувати catch OutOfMemoryError.

**Регресії:** окремий процес з 128 MiB heap; valid/malformed metadata на межі бюджету; скасування; порожні значення; Unicode та підтримувані імена атрибутів.

### F2 — P1 / High: LRU proxy порушує цілісність HLS-плейлиста

**Файл / функція:** [ProxyResourceRegistry.kt:47](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyResourceRegistry.kt:47>), removeEldestEntry/register; MAX_RESOURCES = 512.

**Шлях:** [ProxyServer.serveRewrittenPlaylist:607](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:607>) → M3u8Rewriter переписує всі URI → register для кожного ресурсу → LRU видаляє старі записи → receiver запитує URL із щойно отриманого manifest → [відсутній entry повертає 404](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:247>).

**Тригер:** HLS VOD/архів із 600 різними segment URI проходить через HLS rewrite route. Не потрібен аномально великий файл. Root manifest та вже переписані ранні segments не мають lease/pin, який захищає їх до завершення використання.

**Очікується:** кожен виданий у manifest URL доступний протягом оголошеного/потрібного вікна відтворення; оновлення root manifest також можливе.

**Фактично:** діагностика зареєструвала root playlist і 600 media entries. Результат: resourceCount = 512, rootPresent = false, firstSegmentPresent = false. Root і перший segment витіснені ще до їх нормального використання. Перетворення цього стану на HTTP 404 підтверджене кодом маршрутизатора.

**Межа доказу:** це запуск реального registry, не end-to-end cast на телевізор. Для його невикористаного OkHttp dependency застосовано test stub без конструктора: Android-реалізація OkHttp не ініціалізується в plain JVM через android.util.Log. HTTP-запитів у цій діагностиці не було. Помилка JVM harness не зарахована як помилка Android-застосунку.

**Наслідок:** зрив старту або продовження casting для довгого HLS через proxy. Не всі DLNA-маршрути залежать від rewrite: TS flatten/remux слід перевіряти окремо.

**Виправлення:** lease/reference ownership для активних manifests і ресурсів; контрольоване звільнення після їхнього життєвого циклу. Альтернатива — відновлювані безпечні посилання з перевіркою session/token. Потрібен bounded memory design, а не безмежна map чи просте збільшення 512.

**Регресії:** manifests з 513/600/1000 ресурсів; GET першого/середнього/останнього segment і повтор root; нова сесія звільняє старі ресурси; незалежні headers/referrer не змішуються; тест HTTP-рівня, не тільки кількості entries.

### F4 — P1 / High: XMLTV має квадратичну вартість aliases одного каналу

**Файл / функція:** [XmlTvParser.kt:244](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/epg/XmlTvParser.kt:244>), SAX handler завершення display-name.

**Шлях:** EpgRepository → EpgDocumentPipeline → XmlTvParser → currentDisplayNames.plus(name) для кожного display-name → EpgDataBuilder → EpgIndex.

**Тригер:** один channel містить багато display-name, у тому числі однакових. Для кожного імені List.plus копіює весь попередній список. Ліміт каналів не обмежує aliases одного каналу, а ліміт довжини окремого тексту не обмежує кількість таких текстів.

**Очікується:** приблизно лінійна вартість накопичення назв, бюджет aliases на канал/документ, рання дедуплікація.

**Фактично:** синтетичний XML «один channel + повтор display-name Name» показав:

| Display-name | Розмір XML, байти | Алокації parsing thread, байти |
| ---: | ---: | ---: |
| 2 000 | 66 037 | 16 438 704 |
| 4 000 | 132 037 | 64 832 000 |
| 8 000 | 264 037 | 257 626 168 |
| 16 000 | 528 037 | 1 029 135 344 |
| 32 000 | 1 056 037 | 4 102 347 544 |

Метрика — ThreadMXBean allocated bytes під час parse, **сукупні алокації, не peak heap**. Подвоєння входу дає близько чотириразового зростання алокацій. При 32 000 назв host JVM завершила parse приблизно за 212 ms з GC; це не час Mi A2 і не доказ ANR. Проблема — необмежений квадратичний ріст на невеликому допустимому документі, який посилюється зі збільшенням входу.

**Наслідок:** зайва робота GC/CPU, затримки й енергоспоживання; для значно більшого входу — тяжка деградація. OOM чи ANR саме цього XML на телефоні не заявляються як відтворені.

**Виправлення:** локальний mutable builder із амортизованим O(1) append, дедуплікація, per-channel/total alias cap, видима діагностика обмеження. Не втратити різні мовні варіанти назв. Перевірити cancellation під час наступного index building.

**Регресії:** 2× aliases має давати близький до лінійного приріст роботи; budget одного каналу; багато каналів з типовими aliases; повтори/Unicode; збереження коректного пошуку EPG.

## Проблеми з сильним доказом у коді, без нового end-to-end відтворення

### F3 — P1 / High: невдалий snapshot write не блокує destructive migration commit

**Файли / функції:** [PlaylistSnapshotStore.save:32](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/playlist/PlaylistSnapshotStore.kt:32>), [AtomicFile.writeSafely:31](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/AtomicFileIo.kt:31>), [PlaylistRepository.migrateLegacySnapshotIfNeeded:159](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/playlist/PlaylistRepository.kt:159>), [PlaylistController.loadInitialSource:125](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/PlaylistController.kt:125>).

**Шлях виконання:**

1. Завантажується legacy snapshot.
2. Repository викликає save нової per-source копії.
3. AtomicFile.writeSafely при IOException повертає false; save ігнорує результат та повертає Unit.
4. Repository все одно повертає migrated source.
5. Controller успішно записує короткий список sources.
6. У рядку 135 він видаляє legacy snapshot, перевіривши тільки sourcesSaved, не успішність запису самої копії.
7. Наступне завантаження не знаходить новий snapshot. Для legacy FILE location формується як порожній рядок, якщо sourceUrl відсутній: автоматично перечитати оригінал неможливо.

**Тригер:** оновлення старої інсталяції; для багатомегабайтного snapshot не вистачає вільного диска, write відкочується, але малий sources-файл поміщається. Аналогічний шлях можливий за вибіркової I/O-помилки destination.

**Очікується:** legacy залишається доступним до підтвердження обох записів; повторний запуск може безпечно завершити міграцію.

**Ризик / наслідок:** втрачається збережена в застосунку копія плейлиста. URL-source іноді можна завантажити знову; legacy FILE без збереженого URI потребує ручного повторного імпорту, якщо оригінал ще є. Це не твердження про видалення зовнішнього файла користувача.

**Виправлення:** явний результат durable write; окремий контракт migration, що завершується тільки після успіху snapshot і source list. Видалення старого файла останнє й лише після підтвердженого commit. Best-effort cache contract доречний при звичайному online load, але не при перенесенні єдиної копії даних.

**Регресії:** копія fail + sources success; копія success + sources fail; process death між записами; legacy FILE без sourceUrl. Наявний LegacySnapshotMigrationTest перевіряє успішну міграцію та переривання до saveSources, але не цю комбінацію невдалого запису й подальшого видалення.

### F5 — P2 / Medium: таймер сну не моделює намір завершити відтворення

**Файли / функції:** [PlayerViewModel.kt:104](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:104>), onExpire викликає тільки exoPlayer.pause; [PlayerSleepTimer.kt:23](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerSleepTimer.kt:23>); [onReturnToForeground:703](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:703>).

**Тригер A:** увімкнути таймер до/під час Cast або DLNA і дочекатися завершення. Пункт таймера залишається доступним у [PlayerActionsSheet:53](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerActionSheets.kt:53>).

**Очікується:** відтворення, для якого користувач увімкнув таймер, завершується; або UI заздалегідь чітко обмежує таймер локальним режимом.

**Фактично за кодом:** paused лише локальний Media3; команда зупинки receiver не надсилається. Віддалене відео та пов'язані proxy/service ресурси можуть продовжувати працювати після зникнення timer state.

**Тригер B:** локальне відео → таймер → Home без PiP → onEnterBackground встановлює pausedForBackground → таймер закінчується, поки процес живий → повернення.

**Фактично за кодом:** onExpire не очищає pausedForBackground. Foreground-policy бачить невиконаний resume і викликає play у рядку 721, хоча таймер уже завершився. Аналогічно потрібно узгодити owed remote-resume/retry flags.

**Виправлення:** один playback-stop intent/reason з явним owner; expiry скасовує відкладені відновлення та застосовується до актуального local/remote owner. Не допустити, щоб stop remote через таймер автоматично запустив local. Якщо remote timer не підтримується, це має бути явною capability/UX-політикою.

**Регресії:** timer × foreground/background/PiP × local/Cast/DLNA; handoff під час countdown; expiry і disconnect в одному такті. Unit-тест самого countdown та тест збереження таймера при collapse не покривають ці перетини.

### F6 — P2 / Medium: playback activity flag має неправильного lifetime-власника

**Файли / функції:** [PlaybackActivity.kt](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlaybackActivity.kt>), [PlayerViewModel.onCleared:771](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt:771>), [IconController:91](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/IconController.kt:91>), [MainPlayerZone:44](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/MainPlayerZone.kt:44>).

**Шлях / тригер:** активний receiver → закрити phone player і завершити Activity → ViewModel очищається, але записує PlaybackActivity = isRemoteCasting, тобто true → remote session завершується пізніше, поки application process живий → відкрити home без вибору каналу.

**Очікується:** завершення останнього playback owner скидає activity flag незалежно від наявності player UI.

**Фактично за кодом:** усі три production-виклики setActive знаходяться тільки у PlayerViewModel. Після його очищення Cast/DLNA репозиторії можуть змінити свої states, але flag більше ніхто не оновлює. Home без playerRequest не створює нового PlayerHost/ViewModel. IconController бачить stale true і не запускає background prefetch.

**Наслідок:** попереднє завантаження логотипів може не відновитися до створення нового player або перезапуску процесу. Це **не доказ поломки всіх логотипів**: demand-loading видимих іконок — інший шлях. На low-tier пристроях, де prefetch і так вимкнений політикою, додатковий ефект може бути непомітним.

**Виправлення:** application-lifetime агрегація активних local/Cast/DLNA owners або незалежні leases. Remote owner публікує власне завершення. Не переносити весь ViewModel у singleton і не скидати глобальний flag безумовно при існуванні іншого активного receiver.

**Регресії:** clear ViewModel під час casting → receiver end → новий home без player → prefetch відновлюється; завершення одного з двох owners не відновлює prefetch завчасно.

## Умовний security/availability ризик

### F7 — P2 / Medium: idle timeout не є абсолютним бюджетом HTTP admission

**Файл / функції:** [ProxyHttpServer.kt:41](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyHttpServer.kt:41>), admitConnection, readRequestLines, readHttpLine.

**Доказ у коді:** admission pool = 4, per-IP cap = 8, socket read timeout = 15 секунд. У [рядку 266](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyHttpServer.kt:263>) до authorization читається весь request. Цикл [readHttpLine:451](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyHttpServer.kt:451>) має ліміти байтів, але не загальний deadline читання.

**Умова:** зловмисник у доступній локальній мережі відкриває чотири з'єднання та періодично передає байт раніше за timeout кожного наступного socket read. Так можна тримати всі admission workers зайнятими значно довше 15 секунд, навіть не знаючи session token.

**Очікується:** неповний запит звільняє слот після загального deadline; коректний TV-запит не чекає за повільним неавторизованим клієнтом безконтрольно довго.

**Ризик:** відмова в обслуговуванні proxy/casting у спільній LAN. Це не token bypass, не витік даних і не доведена віддалена Internet-атака. Динамічний slowloris PoC у цьому аудиті не запускався.

**Виправлення:** загальний header/admission deadline, врахування часу очікування в queue, remaining-time read budget; за потреби early token parse або неблокуюче читання. Пер-IP квота не повинна дозволяти одному IP монополізувати весь admission pool.

**Регресії:** кілька slow clients з drip-feed; після total deadline їхні sockets закриті, healthy request обробляється; stop/restart не залишає чергу/leases. Наявні тести перевіряють malformed/oversized requests, дев'яте з'єднання й shutdown, але не drip-feed понад total budget.

## Вимкнений функціонал: не зараховано як активну production-помилку

### L1 — виправити перед STORE_IS_LIVE = true: Unknown purchases трактуються як Known empty

**Файли:** [PlayBillingProvider.kt:78](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/premium/PlayBillingProvider.kt:78>), connect/refreshPurchases; [PremiumRepository.kt:141](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/premium/PremiumRepository.kt:141>), observeProvider/applyPurchases.

**Шлях:** cold start з кешованою paid license → новий provider має початковий empty purchases set → Billing service успішно під'єднався → перший owned-purchases query не вдався → refresh залишає початковий set і CONNECTED → repository combine отримує «connected + empty» → applyPurchases записує License.FREE у рядку 204.

**Проблема:** transport connection і достовірність ownership snapshot — різні стани. Збереження «попереднього результату» при query failure захищає тільки provider, який уже має відповідь у пам'яті; fresh provider її ще не має.

**Наслідок після ввімкнення:** тимчасова billing/network помилка може скинути кешовані платні права. Потрібні Unknown/Known/Failed ownership state або еквівалентний authoritative-result контракт. Лише успішний порожній query може відкликати entitlement; потрібен узгоджений результат усіх підтримуваних product types.

**Чому не активний дефект поточного build:** [PremiumAvailability.kt:31](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/premium/PremiumAvailability.kt:31>) встановлює STORE_IS_LIVE = false, а [AppViewModel:210](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/AppViewModel.kt:210>) обирає FakeBillingProvider. Сам прапорець не змінювався.

**Регресії перед продажами:** cached paid + setup success + first query failure; partial success для різних product types; reconnect failure; справжній successful empty snapshot таки скидає license. Потім — тест через Play test track. Поточні repository-тести «disconnected не відкликає» та «connected empty відкликає» не доводять коректність реального adapter на cold query failure.

## Архітектура, state ownership та UI

### Що вже зроблено правильно

- Є реальна JVM-межа app → core; core не має app/Android на compile classpath. baselineprofile є measurement-модулем, а не runtime-шаром.
- Detekt baseline порожній; перевірка архітектурних залежностей проходить. Кількість рядків сама по собі не використана як доказ порушення MVVM.
- PlayerViewModel володіє Media3/player session і має release/listener cleanup; відкриття/закриття player UI не повинно щоразу створювати незалежний player. Звичайні lifecycle/retry сценарії вже мають захист і тести.
- Cast має перевірки callback owner/content/generation; recovery budget і session/proxy ownership частково відокремлені. Не підтверджую старі загальні твердження «будь-який late callback обов'язково зіпсує новий канал».
- DLNA використовує generation/серіалізацію команд і watchdog; це суттєво зменшує ризик старих команд під час перемикання.
- Proxy вже має token, byte/header/per-IP/queue bounds, окремий response pool і закриття sockets при stop. F2/F7 показують конкретні межі цих захистів, а не їхню повну відсутність.
- AtomicFile та mutation-generation guards захищають багато кешових операцій. F3 — помилка контракту між шарами, а не аргумент відмовитися від atomic writes.
- У merged Play manifest немає REQUEST_INSTALL_PACKAGES; власний foreground service і providers не exported. Exported ProfileInstallReceiver обмежений permission DUMP — не зарахований як незахищений довільний receiver.
- Дозволений cleartext для IPTV/LAN не тотожний trust-all TLS чи доказу витоку. У переглянутих шляхах є централізована санітизація логів; конкретного нового витоку секретів не підтверджено.

### Реальні архітектурні вузли

1. **Контракти durability:** звичайний cache write та migration commit використовують save з однаковим Unit-контрактом, хоча ці операції мають різну допустимість відмови. Вимірювана мета виправлення — жодна втрачена legacy-копія при failure injection (F3).
2. **Ownership виданих proxy URL:** registry трактує entries як незалежний кеш, хоча manifest обіцяє receiver доступність набору ресурсів. Мета — відсутність self-generated 404 при обмеженій пам'яті (F2).
3. **Playback intent розподілений між timer, flags, remote state і lifecycle:** окрема пауза Media3 не є завершенням user intent. Мета — timer/handoff/background мають узгоджений кінцевий owner/state (F5).
4. **Lifetime глобального state довший за publisher:** PlaybackActivity не повинен залежати від існування player screen/ViewModel для завершення remote work (F6).
5. **Неповний input budget:** ліміт bytes не захищає від amplification кількості об'єктів і квадратичної роботи. Мета — передбачувані memory/CPU bounds M3U/XMLTV (F1/F4).
6. **UI не всюди виражає target/capabilities:** меню має локальні track/quality/volume дії і sleep timer під час remote playback. Наприклад, [PlayerSheets:87](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerSheets.kt:87>) керує локальними tracks, а [PlayerLevelsSheet:57](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerLevelsSheet.kt:57>) — AudioManager телефона. Варто явно позначати target або вимикати непідтримувані remote дії. Це рекомендація на базі командних шляхів, не новий візуальний device-баг; F5 порахований тільки один раз.

Великі файли залишаються: CastSessionRepository — 814 фізичних рядків, PlayerViewModel — 804, AppViewModel — 737, ProxyServer — 633, HomeScreen — 619, DlnaSessionRepository — 572, ProxyHttpServer — 544. Це орієнтир вартості підтримки, не standalone severity. AppIcons з 1241 рядком vector geometry не є пріоритетом архітектурного розділення.

### Top 10 ризиків за пріоритетом

1. OOM на допустимому за розміром M3U (F1).
2. Втрата legacy snapshot на storage failure (F3).
3. Недійсні proxy URL у вже виданому HLS manifest (F2).
4. Квадратична обробка XMLTV aliases (F4).
5. Неповний stop intent таймера на local/remote/lifecycle перетинах (F5).
6. Stale application-wide playback flag після знищення ViewModel (F6).
7. Відсутній загальний deadline приймання HTTP headers (F7, умовний LAN-ризик).
8. Конфляція Unknown і empty ownership у Billing (L1, неактивний зараз).
9. Недостатня інтеграційна coverage між уже добре протестованими локальними policy/helper класами: migration commit, emitted HLS URLs, timer + handoff, owner destruction.
10. Неперевірені в цьому проході реальні receiver/OEM/Play сценарії та performance budgets на ART. Це прогалина доказовості релізу, не вигаданий десятий баг.

### Файли першого пріоритету і порядок змін

1. PlaylistSnapshotStore / PlaylistRepository / PlaylistController: durable migration outcome + failure-injection регресії. Першим усунути ризик втрати даних.
2. M3uParser / XmlTvParser: структурні input budgets та лінійна обробка. Не повертати важкі regex; зафіксувати Unicode/cancellation/performance тести.
3. ProxyResourceRegistry / ProxyServer / M3u8Rewriter: життєвий цикл активних manifests/resources з реальним loopback HTTP-тестом.
4. PlayerViewModel / PlayerSleepTimer: єдиний stop intent, без подвійного resume; потім UI capability/target ясність.
5. PlaybackActivity / IconController / application-level remote owners: прибрати залежність глобального flag від короткоживучого publisher.
6. ProxyHttpServer: абсолютний admission deadline і fairness-тести.
7. PlayBillingProvider / PremiumRepository: authoritative ownership state до ввімкнення магазину.
8. Після кожного пункту — focused regression, потім повний gate. Після playback/parser змін — окремий збережувальний прогін на Mi A2; після proxy змін — receiver tests.

Не рекомендую зараз масово створювати нові feature-модулі чи розбивати Compose-файли тільки за кількістю рядків. Спочатку виправити конкретні контракти та ownership; виділяти компонент лише якщо він забирає відповідальність разом зі станом, cancellation і тестами.

## Перевірки та прогалини

### Що виконано зараз

- Повторно запущено Gradle gate: core:test, app:verifyRoborazziDebug, core:detekt, app:detekt, app:lintPlay з continue/offline/no-daemon. BUILD SUCCESSFUL, exit 0; більшість тестових/аналізаторних задач UP-TO-DATE. Це **не 2093 нові виконання тестів**.
- Перечитано актуальні test-result XML: app — 1979 тестів у 322 suites; core — 114 у 13 suites; разом 2093, failures/errors/skipped = 0. Це наявні результати, а не нове покриття F1–F7.
- Усі дев'ять repository guards повторно пройшли: architecture boundaries, hardcoded colors, glow-as-text, locale format, doc references, cast-icon placement, detekt baseline, premium purity, sold-features gating. Baseline — без suppressed findings; 7 sold features мають gates.
- Нові ізольовані adversarial діагностики F1/F2/F4 виконано, результати наведено вище. Вони не були додані як постійні automated tests у цьому read-only аудиті.

### Не плутати з попереднім проходом

31 instrumented-сценарій Mi A2 та повтор 4 playlist-сценаріїв пройшли раніше; їхній звіт — [PLAYLIST_PARSER_FIX_VERIFICATION_2026-09-06.md](<C:/UA CAST/docs/PLAYLIST_PARSER_FIX_VERIFICATION_2026-09-06.md>). Зараз новий phone suite не запускався. Попередній результат не доводить відсутність нинішніх hostile-input і cross-lifecycle проблем.

### Матриця відсутніх регресій

| Область | Наявна coverage | Що саме додати | Тип / пріоритет |
| --- | --- | --- | --- |
| M3U metadata | Parser/fuzz/hostile attribute/cancellation тести | Мільйони непорожніх EPG values під byte cap, бюджет результату | Isolated JVM 128 MiB + Android smoke / P1 |
| Migration | Успіх, переривання до source-list commit | Failed snapshot + successful source-list + не видаляти legacy | Robolectric I/O fault injection / P1 |
| HLS registry | Resource identity, dedup, remux reuse, routing | Fetch усіх URL з manifest >512 entries; root refresh | Loopback HTTP integration / P1 |
| XMLTV | Parser/fuzz/retention/time/index | Один канал з великою кількістю aliases, allocation growth | Host performance budget + ART profiling / P1 |
| Sleep timer | Countdown, collapse survival, окремі lifecycle policies | Expiry × background × handoff × disconnect | ViewModel integration + instrumented / P2 |
| Icon prefetch | Activity policy, selection, races/cancellation | Remote end після onCleared, новий home без player | Lifecycle/owner integration / P2 |
| Proxy admission | Headers/count/IP quota/stop/restart | Drip-feed довше total deadline і healthy client fairness | Controlled socket test / P2 |
| Billing | Disconnected/connected-empty policy, entitlement selection | Cold connected service + failed first ownership query | Adapter contract + Play test track / перед активацією |

### Що ще не доведено

- Сумісність конкретного Hisense VIDAA, актуальних Chromecast firmware і реальних provider streams після мережевих розривів.
- Повний lifecycle/API/OEM прогін Android 8–16. Merged manifest і source guards не замінюють його.
- Peak heap, GC pauses та allocations на ART для нових hostile XMLTV/M3U сценаріїв. JVM allocated bytes не дорівнюють Android peak RSS/heap.
- Новий візуальний прохід усіх екранів, TalkBack/D-pad, усіх локалізацій/масштабів шрифту на підключених телефонах.
- Play Billing через реальний test track, Play Console checks, актуальний dependency vulnerability/SBOM scan. Відсутність нового CVE-висновку не означає, що залежності оголошено безпечними.
- Remote CI у цьому аудиті не запускався; Play-публікація/версія/дані телефона не змінювалися.

## Остаточний вердикт

**WOULD YOU RELEASE THIS BUILD? — NO, не для широкого релізу в поточному стані.**

Причина — не стиль і не бажання нескінченно рефакторити: є відтворений OOM на вході нижче заявленого ліміту; proxy сам робить частину виданих HLS URL недоступною; migration не перевіряє запис перед видаленням єдиної legacy-копії; XMLTV має виміряний квадратичний allocation path. Це конкретні P1, які зелені наявні тести не закривають.

Архітектурна база й захисні механізми вже суттєві; переписувати застосунок з нуля не потрібно. Наступний результат має бути вузьким і перевірюваним: виправити F1–F4 з регресіями, закрити lifecycle/таймер/prefetch і proxy deadline, а потім повторити release gate та потрібні device/receiver перевірки. Повторний вердикт можливий за результатами цих перевірок, а не тільки успішної компіляції.
