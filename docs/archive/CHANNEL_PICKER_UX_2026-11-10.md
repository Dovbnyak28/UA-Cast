# UA-Cast — пошук і вибір каналів плеєра, 2026-11-10

## Зміни цього етапу

Оновлено список каналів у телефонному плеєрі. Пошук має іконку, очищення запиту
та явну IME-дію Search. Поточний канал позначений галочкою й тональним фоном;
ознака більше не залежить лише від кольору назви. Довгі назви залишаються багаторядковими.

KeyboardController і FocusManager читаються всередині контенту ModalBottomSheet:
дія Search приховує клавіатуру та завершує редагування саме в діалозі.
Loading/no-results отримали polite live region для screen reader.
Існуючі debounce 180 ms, фоновий cancellable пошук, stable keys та callback з індексом
оригінальної playback session збережені. Вибір і закриття відбуваються через попередні callbacks.

## Що підтверджено до виправлення

- У початковому UI немає кнопки очищення. Тест сценарію «нічого не знайдено → очистити»
  не знаходив потрібної дії.
- Semantics поля мали ImeAction.Default, замість явно заданого Search.
  Після виправлення тест також перевіряє втрату input focus і доступність результату.
- Візуальна перевірка та посилений тест вікна 640×240dp зі шрифтом 200% виявили
  підрізання поточного рядка на 6dp: viewport 191..240dp, рядок 191..246dp.
  Зменшено вертикальні відступи пошуку з 8 до 4dp. Весь рядок тепер має вміщуватися
  у viewport; текст і критерій перевірки не зменшувалися.

Початковий набір: 7 тестів, 2 UX-контракти не виконувалися, 5 поведінкових сценаріїв
проходили. Окреме відтворення підрізання — 1 failed test. XML та початкові знімки збережені
в reproduction/ і layout-review/; це не перелік підтверджених crashes.

## Захист від регресій

7 тестів PlayerChannelSheetUsabilityTest перевіряють:

- Search IME, завершення редагування та подальший вибір;
- очищення порожнього результату й повернення поточного каналу;
- оригінальний session index після фільтрації та один callback вибору/закриття;
- заміну session list під час пошуку без старих рядків чи старого індексу;
- збереження запиту через StateRestorationTester;
- фактичний fontScale 2.0, довгу назву без text overflow;
- повні геометричні bounds поточного рядка в короткому landscape-вікні.

StateRestorationTester перевіряє saved state композиції, не повний Android process kill.
Новий Android-сценарій окремо вводить відсутній запит, очищує його, шукає Channel 3,
виконує IME Search і перевіряє реальне перемикання PlayerViewModel на третій канал.

Повний JVM/screenshot gate: app 2935 + core 133 + decoder 5 = **3073 PASS**,
0 failures/errors/skips. Цільовий набір із посиленим geometry-критерієм — 7 PASS.
Lint Debug/Release/Play: 0 errors, 0 warnings; по 2 інформаційні hints інструментарію.
Detekt app/core: 0 findings; baseline порожній. Архітектурні межі, кольори та Cast placement — PASS.

Android API 36: **23 PASS**, 0 failures/skips, 93.319 s. Це 18 UI-сценаріїв,
1 fullscreen/реальні кадри/збереження native views та 4 повернення TV-фокуса.
Новий сценарій пошуку пройшов на фактичному MainActivity/PlayerViewModel.
Прогін виконано окремо від Gradle-навантаження. Crash buffer: 0 bytes.
Відновлено початкові дані debug-пакета та звірено контрольні суми 45 файлів.
verified-results.json містить остаточні результати; 1422 зафіксовані файли після тестів не змінилися.

## Перевірка телевізійного маршруту

Гіпотеза про відсутність TvDialogInputRegistration у цьому picker не стала production-багом:
ThemedPlayerScreen при LocalTvMode переходить у TvPlayerScreen і завершує функцію через return
до створення телефонного PlayerChannelPicker. Реальні TV-діалоги audio/subtitles використовують
TrackPickerDialog із явною реєстрацією. Їхні наявні input/lifecycle тести входять у повний gate.
Власники TV remote input, Cast/DLNA та Media3 не змінювалися.

## Вартість і межі

Додано лише векторні UI-іконки й статичне підсвічення видимого рядка. Логотипи каналів
не завантажуються цим списком. Нових залежностей, фонових задач чи animation clock немає.
Перевірка швидкодії FPS/heap не проводилася. Приватні плейлисти не вбудовувалися.
Попередній невідтворений fullscreen timeout і performance budget цим етапом не оголошуються виправленими.

## Артефакти

Каталог build/channel-picker-ui-2026-11-10/:

- reproduction/, reproduce.log — початкові невиконані UX-контракти;
- layout-review/, short-row-diagnostic.log — окремий дефект геометрії;
- previews/, focused-final.log — перевірені кінцеві знімки та цільові тести;
- build-final.log, final-gates.log, junit/, final-junit-counts.json;
- native-api36.log — Android-перевірка;
- native-screens/ — знімки фактичного Android UI із синтетичними каналами;
- before/, stage-source.diff, changed-files.json — зміни цього етапу;
- final-source-fingerprint.json — зафіксовані джерела та візуальні еталони;
- UA-Cast-channel-picker-debug-universal.apk, apk-info.json, apk-code-probe.json.

Debug APK: com.uacastplayer.debug, 0.9.7 (164), 39,657,223 bytes;
arm64-v8a / armeabi-v7a / x86_64. Вбудованих плейлистів і відео немає.
SHA-256: `85a460ff5c506aad4ee6453b4e180e503272d64be39d1a30f7ebf156ad83c229`.
У DEX перевірено наявність нових helper-функцій і tag поля пошуку.
SHA-256 APK, встановленого під час Android-регресії, збігається з переданим файлом.
Повідомлення недоступного потоку у фоні Android-знімка належить тестовому fixture;
цей сценарій не перевіряє доступність ефіру провайдера.

Фізичні пристрої не використовувалися. Після перевірок завершено лише власний AVD
ua-cast-branding. Комп’ютер не вимикався. GitHub-публікація, CI і production release не виконувалися.
