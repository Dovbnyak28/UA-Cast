# Виправлення після дебагінгу — 14 вересня 2026

База: `847ffe6e8af356b3b49d2f9f8fcb4ceb78300fe5`. Зміни локальні, без підняття версії або публікації релізу.

## Підсумок

Усі чотири підтверджені проблеми з [аудиту](<C:/UA CAST/docs/DEEP_DEBUG_2026-09-14.md>) виправлено. Додатково усунуто аналогічний fail-open у перевірці посилань документації. Нові функціональні регресії проходять; **повний test gate поки не зелений** через два Windows loopback timeout. Непідтверджені TCP/JVM обходи не залишені у production-коді.

## Внесені зміни

### D1 — встановлення APK поза Main

[UpdateInstallController](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/UpdateInstallController.kt>) виконує suspending installer на інжектованому IO dispatcher. [ApkInstaller](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/data/update/ApkInstaller.kt>) також має власну main-safe межу, тому перевірка підпису, копіювання та fsync не покладаються на дисципліну майбутнього caller.

Під час staging зберігаються обмежені ранні failure outcomes; після повернення sessionId застосовується тільки відповідний результат. Стара сесія не може зламати нове встановлення. Single-flight та заборона clearOutcome під час активної операції збережені.

Копіювання перевіряє скасування між блоками. Незакомічена PackageInstaller session звільняється при помилці/скасуванні. Після commit її власником є Android, а не вже закритий екран.

Перевірки: [UpdateInstallConcurrencyTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/app/UpdateInstallConcurrencyTest.kt>), [ApkStagingTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/update/ApkStagingTest.kt>) та наявні controller tests. Покрито відсутність блокування Main, ранній callback, сторонній sessionId, повторне натискання, повний/частковий запис, disk failure та cleanup session.

### D2 — достовірна гучність DLNA

[DlnaSessionRepository](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/dlna/DlnaSessionRepository.kt>) більше не відкочує гучність до попереднього optimistic значення. Після SetVolume виконується GetVolume незалежно від прийняття, відмови або втрати відповіді. Якщо read-back теж не вдався, стан стає невідомим (`null`), а не вигаданим значенням; UI приховує недостовірний volume control.

Під час переходу між каналами/пристроями нові volume commands не приймаються. Generation і device identity захищають від старої відповіді після stop/reconnect.

[DlnaVolumeLifecycleTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/dlna/DlnaVolumeLifecycleTest.kt>): reject/reject, accept/reject, clamping телевізором, застосування команди з втратою відповіді, відсутній read-back і disconnect під час читання — 6 сценаріїв.

### D3 — новий запит запускає нову сесію

[PlayerViewModel](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerViewModel.kt>) встановлює autoplay intent для нового явного запиту після заміни media item. Закрита paused session не залишає наступний канал на паузі. Той самий retained request після rotation/collapse зберігає паузу.

Remote ownership не запускає локальний player. Запит у background не готує потік до повернення у foreground.

[PlayerFreshRequestTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/player/PlayerFreshRequestTest.kt>): fresh reopen, retained reattach, background/foreground та remote ownership.

### D4 та додатковий D5 — shell gates fail closed

[check-detekt-baseline.sh](<C:/UA CAST/scripts/check-detekt-baseline.sh>) перевіряє інструментарій і статус обчислення окремо від readonly. Відсутність збігів не плутається з помилкою читання; нечисловий результат відхиляється.

Під час перевірки знайдено таку саму проблему у [check-doc-references.sh](<C:/UA CAST/scripts/check-doc-references.sh>): відсутній `rg` раніше завершувався повідомленням OK. Тепер відсутній інструмент та search error завершують gate з помилкою.

8 негативних/контрольних тестів baseline та 5 tests documentation gate виконуються в `android-ci.yml` і `signed-release.yml`. Реальний baseline залишається порожнім; suppressions і правила detekt не послаблювалися.

## Відкрите O1 — Windows loopback timeout

Додано [ProxyLargeResponseTest](<C:/UA CAST/app/src/test/kotlin/com/uacastplayer/data/cast/ProxyLargeResponseTest.kt>): три одночасні TCP-клієнти, 256 KiB payload, малий receive buffer, перевірка всіх байтів і EOF. Test timeout залишився 5 секунд. У failing run отримано 262 082 з 262 144 bytes при `finished=3, active=0`.

Перевірені й відхилені припущення:

- `shutdownOutput()` саме по собі не прибрало дефект;
- half-close з bounded drain дав успішний окремий прогін, але збій повторився;
- TCP_NODELAY та дрібніші записи у standalone control не дали стабільного результату;
- мінімальний Java TCP-сервер **без жодного класу UA Cast** теж відтворив таймаут читання хвоста на локальному JDK 21; це ще не доказ втрати TCP-пакетів;
- JBR 25.0.3 пройшов 15/15 окремих control transfers, повний debug та release, але повний Play-прогін знову отримав timeout. Отже, JVM upgrade не оголошується виправленням.

Production TCP shutdown та налаштування JVM повернені до початкового стану. Не додано linger, довільного збільшення timeout, retry або виключення тестів. Новий stress test залишений у штатному наборі, щоб збій не був прихований.

Переглянуто [HTTP/1.1 teardown](https://www.rfc-editor.org/rfc/rfc9112.html#section-9.6) та [Microsoft Winsock shutdown guidance](https://learn.microsoft.com/en-us/windows/win32/winsock/graceful-shutdown-linger-options-and-socket-closure-2); вони пояснюють, чому перевірявся staged close, але не доводять конкретну причину цього збою. Локальні повтори не підтвердили достатність такого виправлення.

Точно встановлено: симптом можливий поза кодом proxy. **Не встановлено:** конкретний дефект Windows/JVM/filter driver, а також зв'язок з Hisense VIDAA `Error de red`. Для закриття потрібні TCP trace контрольних loopback connections і незалежний повтор того самого payload на Linux/Android. Змінювати Android proxy навмання під цей host-only сигнал небезпечно. Локальний `wsl --list --quiet` не повернув жодного встановленого дистрибутива; нове Linux-оточення не встановлювалося.

Діагностичний Java control і XML перед/після експериментів збережені під `build/deep-debug-fixes-20260914/`; це generated artifacts, які може видалити `clean`.

## Результати перевірок

Повний unfiltered matrix з тестовим JBR 25.0.3, Gradle/компілятор залишалися на JDK 21:

| Набір | Тестів | Падінь |
|---|---:|---:|
| core | 119 | 0 |
| app debug | 2085 | 0 |
| app release | 1870 | 0 |
| app Play | 1870 | 2 |

Обидва Play failures — `ProxyLargeResponseTest` та HTTP-сценарій `ProxyManifestLeaseTest`. Усі нові перевірки інсталятора, гучності й playback request пройшли у всіх трьох варіантах. Загалом **5944 виконання тестів, 2 failures**, не 5944 унікальні тести. Суму виправлено при звірці з XML: у попередній редакції було арифметично помилкове 5844. Різниця debug/release/Play пов'язана з уже наявним виключенням Compose-manifest tests у release-like variants.

- Baseline tests: 8/8 PASS.
- Documentation gate tests: 5/5 PASS.
- Поточний baseline, architecture boundaries, documentation references: PASS.
- Початкові три `DeepDebug*` repro tests на стандартному JDK 21 після виправлень: 3/3 PASS, без зміни очікувань.
- `:app:detekt`, `:core:detekt`: PASS.
- `:app:lintDebug`, `:app:lintRelease`, `:app:lintPlay`: PASS; у кожному звіті 0 errors, 0 warnings, 3 hints. Підказки стосуються targetSdk 36 та доступних оновлень core-ktx/OkHttp; версії залежностей і target SDK у цьому bug-fix не змінювалися.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`: PASS. Це складання APK, не запуск instrumented tests на пристрої.
- `git diff --check`: PASS.

Не робилося: установлення на телефони, реальний Hisense/Chromecast тест, оплата через Google Play, публікація GitHub релізу. Закриття чотирьох початкових багів не означає закриття O1 або підтвердження сумісності телевізора.
