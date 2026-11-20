# Авторське право й юридична готовність UA-Cast

Дата: 11 жовтня 2026. Видавець за відповіддю власника — фізична особа;
плановані ринки — Україна та/або РФ.

## Висновок

**Не рекомендую новий публічний чи комерційний реліз до закриття ліцензійних
блокерів NextLib/FFmpeg.** Це стосується також уже опублікованого v0.9.7.
Технічні тести не підтверджують юридичну чистоту.

Це evidence-based технічний compliance-аудит, не висновок адвоката, суду чи
правовласника. Нижче розділено підтверджені факти та незакриті ризики.
Окремі дозволи/договори поза репозиторієм можуть змінити оцінку: їх не надано.
Юридична й податкова резидентність видавця невідома; часовий пояс не є її доказом.

Код, legal HTML, APK на телефонах, релізи й ліцензію власного коду не змінено.
Створено лише цей звіт та ignored audit evidence. Комерційної ліцензії для
GPL-компонента чи дозволу на релікензування від власника не припускалося.

## Що реально перевірено

- Поточні Android/core source, Gradle runtime dependencies, legal HTML і ресурси.
- AAR `nextlib-media3ext:1.10.1-0.13.0` та його офіційний Maven POM.
- Native build configuration/license strings і склад локального universal debug APK.
- Публічний arm64 APK v0.9.7, release notes і version catalog його git tag.
- Походження/атрибуція TV wordmark, shipped raster assets, user logo-pack model.
- Cast SDK terms/data disclosures, public privacy URLs і Google Play billing для РФ.

Не виконано exhaustive plagiarism scan кожного рядка всього світового коду,
формальний пошук торговельних марок, патентний clearance, перевірку договорів
правовласників, Play Console/Data safety форми чи перехоплення SDK-трафіку.
Немає висновку про кримінальне правопорушення або автоматичну санкцію магазину.

### Артефакти

| Артефакт | SHA-256 / результат |
| --- | --- |
| Local universal debug APK | `0E346E3D3797F87C17F866323F41CB3712036493B9F46ED42071D134C18840F2` |
| Public v0.9.7 arm64 APK | `9129D05F3C2C80796482D28C99A6547402A12B270EA37EBB6A11C97207B84BB2` |
| Release runtime graph | NextLib 1.10.1-0.13.0, Cast framework 22.3.1, Billing 8.0.0 підтверджені |

Публічний APK отримано з [v0.9.7](https://github.com/Dovbnyak28/UA-Cast/releases/tag/v0.9.7),
без встановлення. SHA ідентифікує перевірені bytes, не доводить авторство чи права.
Ignored evidence: `build/legal-audit/public-v0.9.7-arm64.apk` та
`build/legal-audit/release-runtime-dependencies.txt`.

## Підтверджені compliance-прогалини

### L01 — Critical / P0: GPL-3 NextLib включений без належного distribution package

**Файли:** [dependency](<C:/UA CAST/app/build.gradle.kts:338>),
[версія](<C:/UA CAST/gradle/libs.versions.toml:20>),
[renderer binding](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/player/PlayerRenderersFactoryProvider.kt:25>),
[умови](<C:/UA CAST/legal/terms-of-use.html:89>).

**Доказ:** офіційний [POM точної версії](https://repo.maven.apache.org/maven2/io/github/anilbeesetti/nextlib-media3ext/1.10.1-0.13.0/nextlib-media3ext-1.10.1-0.13.0.pom)
декларує GNU General Public License v3.0. Бібліотека не test-only: factory
прямо використовується player, а native wrapper включений до APK.
[Upstream license](https://github.com/anilbeesetti/nextlib/blob/main/LICENSE)
також GPL-3. Tag v0.9.7 використовує той самий Maven coordinate.

У перевірених APK немає повного GPL-тексту; у поточному source та root публічного
tag немає application GPL grant / LICENSE / COPYING. GitHub license endpoint
повернув 404; release notes не містять GPL/ліцензійних повідомлень. Terms заявляють
власність над кодом застосунку без OSS-виключень. Окремий linking exception або
дозвіл автора NextLib не знайдено.

**Чому це важливо:** звичайне GPL-тлумачення прямого linking не дозволяє
поширювати combined work як довільно закритий proprietary продукт без виконання
GPL або окремого дозволу. Публічна доступність коду на GitHub сама не замінює
ліцензійний grant і повний Corresponding Source/build instructions.
Слова «усі права захищено» самі по собі не заборонені; проблема — відсутні права
та матеріали, які вимагає dependency license. GPL допускає платне розповсюдження,
але не зняття прав одержувача на дозволені модифікації/подальше поширення.
[GPL text, sections 4–6](https://www.gnu.org/licenses/gpl-3.0.html).

**Наслідок:** обґрунтований ризик претензії правовласника й вимоги припинити
неправильно оформлене розповсюдження. Факт судового встановлення порушення відсутній.

**Виправлення — потрібне рішення власника:**

1. Зберегти NextLib і підготувати GPL-compatible distribution: права на власний
   код, license/notices, повний відповідний source/build package; окремо перевірити
   сумісність Google SDK або отримати потрібні additional permissions.
2. Або прибрати/замінити GPL-wrapper на правомірно доступну альтернативу з потрібними
   decoder capabilities. Вимкнення extension renderer не прибирає license obligations,
   якщо бібліотека/її binary залишилися в APK. Не можна просто видалити decoder,
   не перевіривши регресію відтворення.

### L02 — Critical / P0: окремі LGPL-3 обов'язки FFmpeg не оформлені

**Файли:** native `.so` із NextLib; [packaging](<C:/UA CAST/app/build.gradle.kts:200>),
`app/src/main/assets/licenses/`, `legal/terms-of-use.html`.

**Доказ:** у cached AAR, local APK і public v0.9.7 присутній `libavcodec.so`.
Його embedded license — LGPL version 3 or later. Configure містить
`--enable-version3`, shared libraries, mbedTLS і libvpx. `--enable-gpl` та
`--enable-nonfree` в отриманому configure string не знайдені.

Отже, **неправильно** називати саме цей FFmpeg build GPL/nonfree; strong GPL
питання L01 походить від NextLib-wrapper. Проте для FFmpeg також потрібні
LGPL/GPL license documents, notices і придатний варіант виконання вимог щодо
відповідного source та modified-library recombination/relinking. У перевірених
distribution матеріалах цього пакета не знайдено. Звичайне Android dynamic
linking усередині підписаного APK не доводить виконання всіх LGPL умов.
[LGPL-3 section 4](https://www.gnu.org/licenses/lgpl-3.0.html),
[FFmpeg legal guidance](https://ffmpeg.org/legal.html).

**Наслідок:** окремий ліцензійний ризик, навіть якщо L01 буде усунуто заміною Java
wrapper. LGPL не означає автоматичний обов'язок відкрити весь власний застосунок.

**Виправлення:** зафіксувати точний FFmpeg commit/version, patches, configure,
версії статично включених компонентів, повні license/notices та matching source
archives. Надати документований сумісний relink/rebuild/install шлях. Перевірити
native dependencies (зокрема libvpx/mbedTLS), а не лише Java Maven graph.

### L03 — High / P1: privacy policy не відповідає всім поточним механізмам

**Файли:** [privacy policy](<C:/UA CAST/legal/privacy-policy.html:50>),
[backup UI](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/BackupDocumentActions.kt:56>),
[encryption](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/app/BackupController.kt:102>).

**Доказані розбіжності:**

- Політика описує експорт як незашифрований; поточний UI вимагає пароль і передає
  його в `BackupCipher.encrypt`. Legacy/plain API paths залишаються, тому потрібен
  точний опис нових encrypted exports і old JSON imports, а не протилежна абсолютна обіцянка.
- Вказані playlist/EPG logo URLs як джерела зображень; поточна UI-модель використовує
  лише явно додані користувачем packs. Обидві мови політики застаріли щодо цього.
- Формулювання «None for analytics» недостатнє для Cast integration: Google описує
  анонімізовані SDK events/metadata, які можуть передаватися в його telemetry pipeline.
  Це не доказ рекламного tracking чи передачі ідентифікованої історії перегляду.
  Документація описує latest SDK; для pinned 22.3.1 потрібна version-specific перевірка.
- Нові local videos і phone↔TV control потребують явного опису LAN-передачі медіа/
  метаданих, SAF grants, exported-file lifecycle та меж видалення даних.

**Наслідок:** inaccurate disclosure і ризик store rejection; SDK behavior не
покривається одним «у нас немає analytics SDK». Обов'язки охоплюють і third-party code.
[Cast data disclosure](https://developers.google.com/cast/docs/android_sender/data_disclosure),
[Google Play User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

**Виправлення:** актуалізувати policy, реальні data flows і per-variant Data safety;
не заявляти нульовий збір без SDK assessment. Вказати support-report purpose,
одержувача/retention/deletion process; uninstall не видаляє експортовані копії чи
вже надіслані листи. Для ринку РФ доцільна зрозуміла російська версія політики:
зараз HTML має English/Ukrainian, хоча app UI підтримує також Russian/Spanish.

### L04 — High / P1 before Play: public privacy URL не підтверджений

**Файли:** `legal/privacy-policy.html`,
[локальний Pages workflow](<C:/UA CAST/.github/workflows/pages.yml:31>).

Три перевірені кандидати повернули HTTP 404:
`/UA-Cast/legal/privacy-policy.html`, `/UA-Cast/privacy-policy.html`,
`/UA-Cast/terms-of-use.html`. У local workflow до Pages потрапляє сторонній
diary site, а не `legal/`. Цей workflow **untracked**: це не доказ, що саме він
активно розгортається на GitHub. Інша зовнішня policy URL у Play Console невідома.

**Наслідок:** наявність HTML в APK не забезпечує публічної policy URL для store
listing. До Play потрібен working publicly accessible, non-geofenced документ,
узгоджений із вбудованим текстом.
[Play privacy requirements](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

**Виправлення:** обрати й перевірити canonical URL, окремий legal-site deployment
і CI HTTP/content check; не публікувати сторонній diary замість сторінок UA-Cast.
Не стверджується, що будь-яка інша можлива URL також не працює.

## Сильні контрактні ризики та питання до запуску продажів

### R01 — High / P1: Cast entry point треба звірити з SDK terms

**Доказаний UI path:** custom devices icon у
[inline header](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerInlineContent.kt:177>) /
fullscreen → modal device picker → єдиний
[native Cast button](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/player/PlayerActionSheets.kt:115>) →
[MediaRouteButton](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/ui/cast/CastButton.kt:25>).

[Cast SDK terms 5.1](https://developers.google.com/cast/docs/terms) вимагають
відповідного Cast entry point на верхньому рівні castable content screens.
Native widget наразі знаходиться лише на другому рівні. Це сильний ризик
невідповідності SDK requirements; прийнятність конкретного modal UX Google
не підтверджена й автоматична санкція не прогнозується.

**Виправлення:** native Chromecast action на верхньому рівні плеєра, а DLNA/
локальний TV remote — окремо зрозумілий action. Можна залишити дії лише в player,
але не припускати, що nested chooser виконує всі SDK requirements.

### R02 — High before payments: seller/Premium terms недостатньо конкретні

**Файли:** `legal/terms-of-use.html`, `values*/strings.xml:terms_s*`,
`premium/billing/PremiumProducts.kt`.

Є псевдонім і contact email, але немає конкретних умов paid entitlement:
що означає lifetime, перелік/межі підтримуваних features, scope updates/support,
restore, припинення доступності app, refunds і statutory rights. Blanket
відповідальність «це лише питання користувача» не захищає видавця від його
власних license/consumer/data obligations. Catch-all про mandatory law у §4
корисний, але не замінює ясних paid terms.

У поточному [PremiumAvailability](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/premium/PremiumAvailability.kt:7>)
`STORE_IS_LIVE=false`, тож **не доведено**, що зараз покупців уже незаконно
позбавлено гарантій/грошей. Monthly/yearly IDs залишені для legacy restore,
не для нового продажу; це не порушення саме по собі.

**Виправлення до монетизації:** оформити identifiable seller/contact і правомірні
paid terms, не підмінюючи платний доступ до software продажем TV channels.
Потрібна індивідуальна перевірка резидентності, статусу діяльності й податків;
створення компанії чи ФОП не оголошується універсальною вимогою без цих фактів.
Для України врахувати
[digital-content law](https://zakon.rada.gov.ua/laws/show/3321-20) і
[e-commerce law](https://zakon.rada.gov.ua/laws/show/675-19).
Повний актуальний текст цих сторінок був недоступний у browsing environment;
точні applicable clauses має підтвердити місцевий юрист.

### R03 — High commercial constraint: Google Play Premium для РФ не працюватиме як заплановано

Єдиний новий product — `premium_lifetime` через Google Play Billing. Google
підтверджує недоступність покупок/paid-app downloads для користувачів РФ;
free apps залишаються доступними. Це store/market restriction, не доказ
copyright infringement чи незаконності нейтрального плеєра.
[Official billing restrictions](https://support.google.com/googleplay/android-developer/answer/11950272?hl=en).

**Виправлення:** розділити supported distribution/commerce strategy для України
і РФ до promises of paid access. Не маскувати країну чи обходити enforcement.
Інший платіжний канал не можна автоматично вважати дозволеним у всіх країнах:
потрібні перевірка platform policies, санкційних контрагентів і applicable laws.
Без відомої резидентності немає blanket-висновку про заборону будь-якого software export.

### R04 — Medium / P2: EPG service permissions не задокументовані

[EpgSource](<C:/UA CAST/app/src/main/kotlin/com/uacastplayer/epg/EpgSource.kt:10>)
містить п'ять preset URL `epg.it999.ru`; homepage зараз веде на `epg.one`.
Право на commercial app integration/data caching не задокументоване в repo.
У перевіреній landing page явного license grant не знайдено; це не доводить його
відсутність в інших документах і не робить усі schedule facts copyrighted.

**Ризик:** описи програм/структура підбірки/база даних можуть мати окремі права,
а доступність endpoint не тотожна дозволу на будь-яке використання.
**Виправлення:** зафіксувати умови провайдера/дозвіл для потрібного use case або
залишити EPG URL виключно user-provided. Не називати EPG піратським без доказів.
[Provider site](https://epg.one/).

### R05 — Medium / P2: raster provenance й brand clearance не закриті

Переглянуто shipped `app_wallpaper.jpg` і `design/ic_launcher-master.png`, який
використаний для launcher assets. Немає задокументованого автора, source URL/
license або generation provenance. Доказів, що ці зображення викрадені, немає.
Generic TV/play/waves самі по собі не доводять trademark infringement.

**Виправлення:** вести asset register (автор/джерело/умови/дата/модифікації/hash),
підтвердити commercial usage права. Для UA CAST name/logo зробити формальний
пошук подібних marks у потрібних класах/територіях; пошук не проведений, назва
не оголошується ні вільною, ні такою, що обов'язково порушує чиїсь права.
Не використовувати copyrighted footage/logos у store screenshots без прав.

### R06 — Medium / P2: patents, content contracts і marketing boundaries

Native build реально містить software H.264/HEVC/AC-3/DTS-family decoders.
FOSS license не є patent clearance для всіх територій/use cases; факт
невиплаченої конкретної royalty не встановлено.
[FFmpeg patent/legal overview](https://ffmpeg.org/legal.html).

README згадує geo-restricted/VPN feeds. Це не доказ DRM breaking у коді,
але marketing не має обіцяти обхід paywall/geo/licensing restrictions.
Права на звичайний перегляд не завжди включають redistributing/casting поза
дозволеним provider use case; особистий LAN relay не називається автоматично
незаконним public rebroadcasting. Для України базова рамка —
[copyright law](https://zakon.rada.gov.ua/laws/show/2811-20), для РФ потрібен
локальний review applicable content/IP rules, не тільки Google policies.

## Що перевірено правильно / хибні звинувачення відхилено

- У переглянутих APK assets немає bundled commercial playlist, movies чи packs
  broadcaster logos. Temporary playlist wiring debug-only, у checked APK його немає.
- Channel icon model user-provided; сама можливість відкрити user URL не доводить
  порушення авторських прав видавцем. Вбудований EPG розглянуто окремо.
- Current TV wordmark має повний Apache-2 license, attribution і modification
  notice в `assets/licenses/tv-wordmark-roboto.txt`. Не називати Roboto «вкраденим».
- APK містить кілька AndroidX Apache license files; твердження «жодних OSS licenses
  взагалі немає» було б неправильним. Проблеми L01/L02 конкретні.
- Test-only JUnit/Robolectric/org.json не прирівнюються до shipped runtime libraries.
- GPL дозволяє commercial distribution; paid app сам по собі не порушення GPL.
- Default Media Receiver ID допустимий без власного receiver registration:
  [official receiver guidance](https://developers.google.com/cast/docs/web_receiver).
- Немає власних account creation flows: не вимагається вигаданий account-deletion UI.
- No ad SDK зараз узгоджується з placeholder ad infrastructure; future ad integration
  вимагатиме нового consent/data/policy assessment, але не оголошується вже активною.
- First-run Terms і offline privacy document доступні; app requests не потребують
  camera/microphone/contacts permissions. Це корисні мінімізації, не повний legal clearance.
- Local untracked diary/Firebase web files і macOS VM — не Android APK contents.
  Вони не оголошуються порушенням UA-Cast, але мають бути excluded з publication
  allowlist: випадкова публікація VM/image/private data створила б інший ризик.

## Порядок виправлень

1. Власник обирає license architecture: GPL-compatible distribution + SDK clearance
   чи заміна NextLib без неконтрольованої втрати decoder coverage.
2. Інвентаризувати всі вже released APK, додати належні license/source packages,
   виправити grants/notices; не обмежуватися одним новим README рядком.
3. Оновити privacy/TOS у всіх in-app translations та HTML, узгодити SDK Data safety,
   backup/local-video/TV transfer і support-report retention.
4. Canonical public privacy deployment, перевірка URL; підкоригувати Cast entry UX
   за SDK requirements без рознесення cast icons по непричетних меню.
5. Підтвердити asset/EPG rights, seller/Premium terms і market commerce strategy.
6. Додати release gate: SPDX/CycloneDX inventory з licenses, allow/deny rules,
   native configuration/source hashes, GNU/NOTICE asset checks і public-policy check.
   Звичайний dependencies.txt та перевірка двох HTML assets цього не забезпечують.

### Остаточний вердикт

**Legal-release readiness: NO, до закриття L01/L02.**
Це не висновок, що user-driven IPTV player як клас заборонений в Україні або РФ.
Найсильніше підтверджені проблеми — власне розповсюдження third-party software
і неповні/застарілі disclosure, а не доведена крадіжка TV-контенту чи графіки.
Зміна ліцензії власного коду або заміна decoder stack потребує окремого рішення
власника; без нього такі зміни не виконані.
