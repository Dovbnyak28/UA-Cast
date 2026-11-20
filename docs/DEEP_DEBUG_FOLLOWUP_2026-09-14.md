# Глибокий дебагінг: proxy lifecycle і Windows TCP — 14.09.2026

Оновлення: після цього аудиту D6/D7 виправлено. [Зміни та результати регресій, включно з Mi A2](<C:/UA CAST/docs/PROXY_RACE_FIXES_2026-09-14.md>). Нижче збережено висновки етапу діагностики до виправлень.

## Висновок

Знайдено **дві нові підтверджені помилки**: P1 у володінні активним remux при запізнілому запиті та P2 у ліміті з'єднань з одного IP. Три failing assertions відображають дві причини, а не три незалежні баги.

Розслідування попереднього O1 просунулося: однаковий TCP-симптом відтворено не тільки у Java, а й у незалежному .NET-контролі на цьому Windows-хості. На Mi A2 пройшли 30 standalone transfers і 30 transfers через **справжній ProxyHttpServer з debug APK**. Це не тест телевізора і не доводить відсутності всіх мережевих дефектів застосунку.

Production-код, конфігурація застосунку, версія і CI у цьому етапі **не змінювалися**. Попередні незакомічені виправлення збережені. Додано тільки діагностичні програми/тести та звіт; нові помилки ще не виправлені.

## D6 — P1 / High: старий канал перехоплює active remux нового

**Файли та функції:**

- [ProxyServer.kt:284](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:284>) — upstream request та передавання response до serving.
- [ProxyServer.kt:353](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:353>) — callback після визначення маршруту, до передачі remux-власності.
- [ProxyServer.kt:413](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyServer.kt:413>) — `serveRemuxedUpstream` передає лише перевірку `httpServer.isRunning`.
- [ProxyResourceRegistry.kt:131](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyResourceRegistry.kt:131>) — `startRemuxSession`.
- [ProxyResourceRegistry.kt:153](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyResourceRegistry.kt:153>) — без перевірки актуальності каналу замінює `activeRemuxSession`.

**Відтворення / execution path:**

1. Receiver починає запит raw-TS каналу A. Origin response уже отримано і класифіковано як REMUX.
2. Handler A затримується перед встановленням remux. У тесті затримка контрольована на `onRouteAttempted`; production callback звертається до синхронізованого `RemuxEffectivenessStore`. Достатньо також звичайної паузи планувальника в цій точці; тест не доводить, що callback завжди повільний.
3. Канал B реєструється й отримує новий активний remux.
4. A продовжує роботу. `isRunning == true`, тому registry приймає його як новий active, а B зупиняє і переміщує в draining.

Однаковий дефект відтворено у двох сценаріях: **A → B у тій самій сесії** і **stop старої сесії → start нової → B**. При звичайному `stop` без restart контрольний тест проходить: наявна boolean-перевірка працює тільки доки сервер не запущено знову.

**Очікується:** після вибору B старий запит A не може змінювати active remux. Попередні вже обіцяні сегменти можна віддати з draining-буфера, але не відновлювати старий producer.

**Фактично:** assertion active resource отримує fingerprint A замість B. Обидва сценарії повторно впали. В одному з тестів pool interrupt зберігається, але не захищає новий remux-thread: той створюється окремо.

**Наслідок:** новий канал втрачає producer; після вичерпання буфера можливі зависання/перезавантаження, зайве читання старого IPTV-потоку та коливання active/draining. Саме підміна і зупинка producer підтверджені тестом; поведінка UI/receiver після цього на реальному Chromecast ще не виміряна.

**Рекомендоване виправлення:** передавати незмінний контекст належності запиту: generation proxy-сервера та generation вибраного каналу. Перевіряти його атомарно з встановленням remux під тим самим lock. Порівняння тільки URL/resourceId недостатньо для A → B → A, а тільки token — для швидких переключень усередині сесії. Відхилений response закривати; застарілий producer не запускати. Зберегти право читати вже готові draining-сегменти без права повернути їм active-власність.

**Доказ:** [DeepDebugProxyGenerationTest](<C:/UA CAST/docs/debug/2026-09-14/tests/com/uacastplayer/data/cast/DeepDebugProxyGenerationTest.kt>) використовує справжні ProxyServer, registry, remux і HTTP sockets. Підмінено лише upstream синтетичними TS-пакетами і момент затримки callback. Reflection використано для читання active-власника під його справжнім lock, а не для підміни бізнес-стану.

## D7 — P2 / Medium: IP-ліміт можна перевищити при звільненні останнього slot

**Файл:** [ProxyHttpServer.kt:368](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyHttpServer.kt:368>) — `acquireIpSlot`; [ProxyHttpServer.kt:377](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/cast/ProxyHttpServer.kt:377>) — `releaseIpSlot`.

**Відтворення:** accept-loop отримує існуючий AtomicInteger через `computeIfAbsent`. Перш ніж виконати CAS, останній старий response-worker зменшує цей counter до 0 і видаляє його з map. Accept-loop успішно збільшує вже від'єднаний counter. Наступне з'єднання створює інший counter, який не враховує попередній живий socket.

**Очікується:** максимум 8 одночасно виданих leases для IP.

**Фактично:** контрольований тест отримує **9 leases замість 8**. Об'єкт-lease захищає від stale release після restart, але не робить lookup/increment атомарними відносно нормального release/remove.

**Наслідок:** ослаблення заявленого per-IP захисту й додаткове навантаження на admission/worker pools під churn. Немає доказу обходу session-token, доступу до приватних потоків або необмеженої пам'яті: загальні bounded queues залишаються. Тому P2, не критична віддалена вразливість.

**Рекомендоване виправлення:** створення/інкремент/перевірку cap виконувати одним атомарним `ConcurrentHashMap.compute` для ключа, сумісним з атомарним release/remove. Зберегти ідентичність lease для захисту між поколіннями та додати race + cap + stop/start регресії.

**Доказ:** [DeepDebugProxyIpLeaseTest](<C:/UA CAST/docs/debug/2026-09-14/tests/com/uacastplayer/data/cast/DeepDebugProxyIpLeaseTest.kt>). Тестовий map зупиняє код після справжнього `computeIfAbsent` і викликає справжній `releaseIpSlot` — це примусове відтворення допустимого thread interleaving, не статистичний stress із надією влучити в race.

## O1 — TCP timeout: нові експерименти

Для всіх контролів: синтетичний payload 256 KiB, три одночасні клієнти, receive buffer 8 KiB, read timeout 5 s, перевірка повного вмісту й EOF. Жодного реального IPTV URL.

| Виконання | Transfers | Timeout/failure |
|---|---:|---:|
| Windows / JDK 21.0.11 / standalone, BufferedInputStream | 9 | 1 |
| Windows / JDK 21.0.11 / standalone, без BufferedInputStream | 9 | 3 |
| Windows / незалежний .NET / NetworkStream | 15 | 10 |
| Mi A2 / Android API 30 / standalone Java через ART | 30 | 0 |
| Mi A2 / HTTP-server з поточного UA Cast debug APK | 30 | 0 |

Windows Java-контроль у момент timeout показав `writerDone=true`, `available=0`, тіло 262082/262144 bytes. Для конкретної пари loopback ports `netstat` показав server `FIN_WAIT_1`, client `ESTABLISHED`. Це спостереження TCP-стану, **не packet capture і не доказ того, який саме компонент винен**.

Таке саме характерне зависання хвоста є у .NET-контролі без JVM. Отже, попереднє припущення про специфічний дефект Java/BufferedInputStream не пояснює весь симптом. Конкретну причину на цьому Windows-хості (стек, драйвер/фільтр чи інший чинник) не встановлено. Висновок про всі Windows-машини робити не можна.

`pktmon status`/`filter list` повернули Access denied навіть із доступом поза sandbox. Захоплення не запускалося; мережеві фільтри, firewall, системні TCP-параметри та драйвери не змінювалися. Подальше звуження потребує прицільного packet trace цих синтетичних loopback-з'єднань із необхідними адміністративними правами.

**Програми для повтору:** [SocketTransferProbe.java](<C:/UA CAST/docs/debug/2026-09-14/SocketTransferProbe.java>), [TcpTransferDotNetProbe.cs](<C:/UA CAST/docs/debug/2026-09-14/TcpTransferDotNetProbe.cs>), [ActualProxyTransferProbe.java](<C:/UA CAST/docs/debug/2026-09-14/ActualProxyTransferProbe.java>).

На Mi A2 APK використано лише як classpath для автономного `dalvikvm`, **не встановлено**. Перевірений APK SHA-256: `C16D396FE54898FAAF7EB9989CE05B1B450C61932CE7840862C801D17ACAE7FE`. Обидва тимчасові файли та їх порожню директорію `/data/local/tmp/uacast-debug-tcp-20260914` прибрано; вихідні копії залишилися в workspace. Дані встановленого застосунку не чіпалися.

## Перевірки, обмеження і наступні кроки

Фінальний діагностичний прогін:

```text
gradlew.bat -I docs/debug/2026-09-14/repro.gradle :app:testDebugUnitTest --tests '*DeepDebug*' --rerun --offline --console=plain --no-configuration-cache --max-workers=2
7 tests: 4 PASS, 3 FAIL
```

Три старі repro для installer/Main, DLNA volume і fresh playback request знову PASS. Контроль `stop` без restart PASS. Два сценарії D6 і один D7 FAIL. XML збережені у [followup-results](<C:/UA CAST/docs/debug/2026-09-14/followup-results>), тож наступний Gradle run їх не затре. Діагностичні тести додаються тільки через init script, штатний CI набір у цьому етапі не змінено.

Це поглиблений етап proxy/network debugging, а не повторний прогін усіх UI, billing, parser та Android lifecycle тестів. Не тестувалися Hisense VIDAA, реальний Chromecast, WAN/CDN, оновлення APK через системний installer або Google Play. Причинний зв'язок між O1 та телевізійним `Error de red` не доведено.

Пріоритет: **спочатку виправити D6**, перевірити A → B → A і старий callback після restart; потім D7 з атомарним quota accounting; Windows TCP досліджувати окремо без непідтверджених змін Android proxy. До закриття P1 D6 реліз рекомендувати не можна.
