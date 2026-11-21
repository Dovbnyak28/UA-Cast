# Перевірка релізного підпису та оновлення без втрати даних — 2026-11-20

Фаза C плану [release hardening](CODEX_PROMPT_RELEASE_HARDENING_2026-11-20.md). Звіт лежить в
`docs/archive/`, як і решта датованих звітів (фаза B).

## Підсумок

| Пункт плану | Стан |
| --- | --- |
| Підписаний release 0.9.7 з поточного HEAD | **Не зібрано** — потрібні `UACAST_STORE_*` (паролі keystore), їх немає в оточенні і вони не повинні проходити через агента |
| `apksigner verify --print-certs`: сертифікат збігається з попереднім релізом | **Так**, для всіх наявних підписаних APK (таблиця нижче) |
| Оновлення зі збереженням даних, API 36 | **Пройдено**: 0.9.3 (опублікований) → 0.9.7 (попередній реліз 14.11) |
| Те саме на API 30 | **Не виконано** — образу API 30 немає в SDK (завантаження ~1 ГБ потребує згоди власника) |
| Перевірка in-app updater на ланцюжку | **Не виконано** — емулятор не проходить TLS до GitHub через перехоплення TLS на хості (той самий збій, що й у Gradle wrapper до `trustStoreType=Windows-ROOT`) |
| Сценарій як скрипт-тест | [`scripts/verify-release-upgrade.sh`](../../scripts/verify-release-upgrade.sh) |

## Сертифікати та SHA-256

Усі підписані APK, що є локально, мають той самий сертифікат підпису
(DN `CN=ROMAN DOVBNYAK, O=UA Cast PLAYER, L=Barcelona, C=ES`, схема v3):

| APK | versionName / versionCode | SHA-256 файлу | SHA-256 сертифіката |
| --- | --- | --- | --- |
| `build/previous-release-0.9.2/app-universal-release.apk` | 0.9.2 / 114 | `3c0ea860295ee11f33108c3e8cecb14301271c71f0432eeb3d8d10db8e325897` | `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e` |
| `build/published-release-0.9.3/app-universal-release.apk` | 0.9.3 / 124 | `1360179fddc17cd0fcf2ece0965f3e98e0782b883c8589719d9901a1b5a54377` | `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e` |
| `build/verification/cinema-v2-stage7-2026-11-14/artifacts/UA-Cast-0.9.7-preliminary-release.apk` | 0.9.7 / 164 | `6974a1b16ae6a2b1b70e6449c7f719b59b3a1440a909932af801eb97e7dd2ad1` | `c040badcb09bd0fd196a09c00f062a8160667cc073a6b5e8a20b0f860a82602e` |

Підписаного **0.9.6 не існує**: у репозиторії є теги v0.9.4, v0.9.5 і v0.9.7, а GitHub Releases
містять 0.9.1–0.9.3 і 0.9.7. Тому базою оновлення взято останній опублікований 0.9.3.

## Сценарій оновлення (емулятор `medium_phone`, API 36, x86_64, 2 ГБ RAM)

1. Чисте встановлення опублікованого 0.9.3, онбординг (українська, умови, пропуск туру).
2. Дані в 0.9.3:
   - два плейлисти за URL з локального HTTP-сервера хоста (`10.0.2.2`, підтвердження HTTP):
     «Ukraine test» (5 каналів) і «Second list» (5 каналів, активний);
   - EPG-джерело з `url-tvg` плейлиста («Телепрограма готова»);
   - улюблені: Channel b1, Channel b2;
   - тестовий PIN батьківського контролю і заблокований Channel b3;
   - тема «Кіно» (вибрана явно), розкладка «Сітка», мова українська.
3. Перезапуск 0.9.3 — дані на місці.
4. `adb install -r` 0.9.7 (vc 164) — `Success`, `lastUpdateTime` оновився, онбординг не показано.
5. Після оновлення:
   - обидва плейлисти в «Збережені плейлисти», активний — «Second list»;
   - улюблені b1, b2 на головній;
   - «Заблоковані канали (1)», «Змінити PIN»; вхід у Channel b3 запитує PIN і після нього
     відкриває плеєр;
   - EPG-джерело показане явною адресою `http://10.0.2.2:8765/epg.xml` (у 0.9.3 — «Джерело з
     плейлиста / власне»): значення збережено, змінилось лише подання;
   - тема «Кіно», розкладка «Сітка», мова українська;
   - у `logcat -b crash` немає FATAL.
6. Повторний прогін `scripts/verify-release-upgrade.sh ... --no-pause "Second list" "Channel b1"
   "Channel b2"` → `OK: upgrade kept the expected data`.

## Ризик для релізу: та сама версія 0.9.7 вже опублікована

На GitHub 2026-09-24 опубліковано реліз **v0.9.7** (universal APK, той самий versionName), а
поточна гілка теж збирає `versionName` 0.9.7 / `versionCode` 16 (universal 164). Користувач
вересневої 0.9.7 не побачить нову збірку як оновлення: in-app updater порівнює версії, а система
дозволить `install -r` лише як «переустановку» тієї самої версії. **Перед релізом підняти версію до
0.9.8 / 17** (або перевизначити через `-Puacast.versionCode` / `-Puacast.versionName` у CI).

## Що має зробити власник

1. Задати `UACAST_STORE_FILE`, `UACAST_STORE_PASSWORD`, `UACAST_KEY_ALIAS`, `UACAST_KEY_PASSWORD`
   (keystore `a-cast-player-release` у корені, ignored) і зібрати підписаний реліз з HEAD:
   `./gradlew :app:assembleRelease` (або запустити `signed-release.yml`).
2. Встановити образ API 30 (`sdkmanager "system-images;android-30;google_apis;x86_64"`), створити AVD
   і прогнати на API 30 та API 36:
   `scripts/verify-release-upgrade.sh build/published-release-0.9.3/app-universal-release.apk
   app/build/outputs/apk/release/app-universal-release.apk "<назва плейлиста>" "<улюблений канал>"`.
3. Перевірити in-app updater на пристрої з прямим доступом до GitHub (без TLS-перехоплення).
