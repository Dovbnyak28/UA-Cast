# Практичність і читабельність UI — 10 жовтня 2026

## Межі перевірки

Переглянуто чинні Home, Settings, navigation і player UI, існуючі golden-знімки,
а також нові макети файлового плеєра. Основні зміни спрямовані на підтверджені
проблеми контексту дій, читання назви та доступності кнопок. Cinema, Azure і
Midnight залишено без зміни палітр; нових бібліотек, растрового декору чи
анімацій не додано. Це не повна сертифікація кожного екрана на кожному TV.

## Виправлення

| Проблема | Результат |
| --- | --- |
| Локальне відео мало TV guide, disabled previous/next channel і LIVE | File mode не пропонує дії телеканалів; LIVE залишається лише для каналів |
| Помилка файла також пропонувала Next channel | Залишено Retry і Back, з правильним accessibility label |
| Довга назва губилася у вузькому заголовку | Окрема картка файла: назва до трьох рядків; великий fullscreen-заголовок отримує окремий ряд |
| Файл не мав видимої шкали часу | Поточний час, тривалість і seek у inline/fullscreen/TV file mode; невідома тривалість не дозволяє перемотку |
| Активний sleep timer стискав fullscreen-кнопку на 320dp | Панель до 600dp має два ряди; перевіряються також розміри, а не лише обрізані координати кнопок |
| Файл звертався до паку логотипів телеканалів | Для файла використовується векторний play glyph без пошуку логотипів |
| Open video був після статистики Home | Дія розташована поруч із View channels, до допоміжних карток |

Наявні channel actions збережені для IPTV. Back не перейменований на Stop:
він має чинну навігаційну поведінку, а не обіцянку зупинити відтворення.
Локальні control/seek не з'являються як керування remote-owned playback.

## Коректність і ресурсна вартість

`FilePlaybackTimeline` — UI-проєкція існуючого Media3 player, не новий owner.
Listener працює лише в STARTED і видаляється при STOP/disposal. Polling із
кроком 500ms запускається лише для playing + STARTED; paused UI event-driven.
Зміна позиції перебудовує timeline-компонент, а не весь екран навігації.
Seek використовує наявний guarded `navigation.seekTo`; UI не готує й не
звільняє player. Підтримано accessibility SetProgress з перевіркою NaN і меж.

Під час нового тесту виявлено точну помилку відображення часу: перетворення
65000ms через Float fraction назад у позицію давало 64999ms і показувало 1:04.
Без drag тепер відображається вихідна позиція: 1:05. Це виправлення текстової
проєкції, а не зміна реального playback clock.

## Докази та регресія

- Тест file-error menu перед виправленням: 1/1 failed через Next channel.
- Compact layout перед виправленням: 4/4 failed у en/uk/ru/es після додавання
  мінімального розміру кнопок. Перевірка лише clipped bounds недостатня:
  нульова ширина кнопки помилково проходила попередній assertion.
- Після виправлення: 28/28 focused cases, зокрема file/live UI на 320dp,
  fontScale 200%, довгий timer, navigation labels у чотирьох мовах, lifecycle
  cleanup paused projection, time/range і відсутність logo lookups для файла.
- Записано лише п'ять нових goldens; попередні 49 не перезаписувалися.
- Повні suites: debug 2741/2741, release 2333/2333, Play 2333/2333;
  перевірка всіх 54 goldens пройшла. Core 133 cases — cached результат.
- Фінальний повний gate: BUILD SUCCESSFUL, 2m39s, 227 tasks (12 executed,
  215 up-to-date). Lint трьох варіантів: 0 errors, 0 warnings, 2 hints;
  detekt, 12 repository checks і benchmark/baselineprofile compilation green.
  Compile-перевірка не є запуском Macrobenchmark чи виміром FPS.
- Mi A2, Android 11/API30: фінальний native run 27/27 за 104,836s,
  включно з actual Media3 seek через UI без зняття паузи й file More menu.
  Protected runner відновив три початкові private file hashes; release app
  не змінено. Native-протестовані APK збігаються за SHA-256 з фінальними APK.

### Розібрані падіння перевірок

Перший native run був 26/27, повтор із capture — 0/1 для нового UI-тесту.
Знімок показав видиму шкалу й український UI. Причина: тест шукав англійські
підписи, зокрема `Video position`. Усі його дії та асерти переведено на
`app.getString(R.string...)`; production layout через це не змінювався.
Перевірка видимості не видалена й не замінена на менш сувору.

Перший повний Play run мав один 5s HTTP timeout у `ProxyManifestLeaseTest`.
Окремий unchanged повтор усіх чотирьох cases пройшов, як і подальший повний
Play run 2333/2333. Timeout, test budget і proxy source не змінювалися.
Першопричина одноразового timeout не встановлена; варто відстежувати повтор
у CI, а не стверджувати, що production network defect доведено й виправлено.

Ignored журнали: `build/file-ui-full-gate-final.log`,
`build/file-ui-mi-a2-final.log`, `build/file-ui-final-results/`,
`app/build/device-audit/instrumented-c235cdc8c56f4ffd984051349d8206d7.txt`.
Фактичний native-знімок: `app/build/ui-usability/file-player-mi-a2.png`.

Нові контрольні знімки:

- `app/src/test/screenshots/file_player_inline_cinema.png`
- `app/src/test/screenshots/file_player_overlay_cinema.png`
- `app/src/test/screenshots/file_player_large_text_cinema.png`
- `app/src/test/screenshots/file_player_compact_uk.png`
- `app/src/test/screenshots/live_player_compact_uk.png`

## Оцінка й подальші можливості

Візуальна мова вже цілісна: акцент відокремлює головну дію, settings поділені
на секції, navigation має текстові назви й тести великого шрифту. Найбільшу
виявлену плутанину створювало змішування файла з телеканалом; цей контекст
тепер явно відрізняється. На великих екранах збережено компактну однорядну
панель; на вузьких пріоритетом є видимі й читабельні дії.

Окремими майбутніми задачами доречні recent video files з коректним SAF
permission handling і світлий/high-contrast варіант для яскравого освітлення.
Їх не додано без окремого дизайну state/permissions та перевірки контрасту.
Реальний Mi TV/D-pad acceptance ще не проведений у цьому проході. Раніше
виміряні frame-time overruns не оголошуються виправленими: compilation і
functional UI-тести не замінюють performance measurements.

Ignored evidence: `build/file-ui-*.log`, `app/build/device-audit/`,
`app/build/ui-usability/`. Recovery archives приватні й не публікуються.
Номер версії, release signing і GitHub publication не змінювалися.
