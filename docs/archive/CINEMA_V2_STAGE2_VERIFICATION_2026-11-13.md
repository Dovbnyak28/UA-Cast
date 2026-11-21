# Cinema V2 — звіт етапу 2, 2026-11-13

## Результат і межі

Етап 2 завершено: семантична Material 3 дизайн-система, типографіка, spacing, кнопки, картки, рядки, TV-фокус, reduced motion та адаптивні сегменти. Детальні правила й API: [CINEMA_V2_DESIGN_SYSTEM.md](../CINEMA_V2_DESIGN_SYSTEM.md).

Структура екранів, маршрути, player state machine, Media3, Cast/DLNA, джерела каналів, Lite/Premium та решта бізнес-логіки не змінені. `UaCard`/`UaListItem` є спільними foundation-компонентами для подальших погоджених етапів; весь каталог не є новим production-екраном. Етап 3 не починався.

Git: гілка `codex/release-0.9.6`, HEAD `d6728a6632d8bde9bd035a561118a621059321d7`. Перед змінами збережено й SHA-256 перевірено 1606 наявних файлів, 36 108 349 байт, з урахуванням усієї попередньої локальної роботи. Дев'ять раніше видалених tracked-файлів залишені як були. Backup: `build/cinema-v2-stage2-2026-11-13/before/`. Git index не змінювався.

## Зміни

- Повний palette-to-ColorScheme adapter: primary/container/on*, secondary/tertiary, error, inverse/fixed, усі surfaceContainer, контрастний outline, status та focus ролі. Виправлено успадкування стандартних Material-відтінків замість активної палітри.
- Cinema — послідовний шампансько-золотий акцент. Azure, Midnight, Daylight та SYSTEM збереглися; переключення теми не перебудовує native child.
- Усі 15 typography roles мають offline SansSerif family та явний line height. Новий `AppSpacing` додає ритм 4/8/12/16/24/32dp без масової зміни старих відступів екранів.
- Icon actions мають власні 48×48dp; primary/secondary actions — мінімум 48dp та Button semantics. Картки й рядки підтримують selected/disabled; рядки мають галочку вибору.
- Press feedback малюється всередині незмінних hit/focus bounds, 120ms, scale 0.97/0.96. Reduced effects або Android animator scale 0 прибирають рух, зберігаючи дію. Один theme-owned observer обслуговує всі controls та знімається при disposal.
- TV-фокус має контрастний роздільник під обводкою. Додатковий focusable не створюється; DPAD пропускає disabled, OK спрацьовує один раз.
- Segmented control вимірює підписи та переходить у повноширинні single-choice варіанти, коли ряд не вміщується. При 320dp/200% немає переносу слів по літерах.

## Що тести виявили до завершення

1. Перший RED: 54 перевірки, 50 failures. Це повторення контрактів у різних темах/масштабах, а не 50 незалежних production-багів. Виявлено недостатні semantic roles, outline contrast, targets та semantics/press geometry.
2. У новому тесті auto-width Text `hasVisualOverflow` дав хибний width-сигнал: paragraph 512px, layout 322px, край літер 320.93px. Перевірка замінена на фактичні line bounds, висоту, ellipsis та втрачені рядки; код не перероблявся заради цього хибного сигналу.
3. PNG при 200% показав перенос назв сегментів по літерах. Окремий RED підтвердив його; адаптивний stack виправив поведінку без зменшення font scale.
4. Повний набір виявив регресію короткого landscape player sheet: viewport 52.5dp для рядка 55dp. Причина — глобальне збільшення `bodyLarge`. Збережено початкові `BodyText` 14.5/21sp; тест знову проходить, файл шторки/логіка каналів не змінювалися.
5. Lint зауваження до порядку нового `modifier` API усунено в нових компонентах і їхніх callers. Пороги, baseline та перевірки не послаблювалися.
6. Mutation check: інверсія умови motion зламала 2 з 3 policy-тестів. Правильний файл відновлено byte-for-byte до фінальних gates.

## Фактично виконані тести

| Набір | Tests | Failures/errors | Skipped |
|---|---:|---:|---:|
| app debug, включно з Compose/Roborazzi | 3073 | 0 | 0 |
| app release unit | 2470 | 0 | 0 |
| app Play unit | 2470 | 0 | 0 |
| core unit, примусовий свіжий запуск | 133 | 0 | 0 |
| FFmpeg debug unit, свіжий запуск | 5 | 0 | 0 |
| FFmpeg release unit, примусовий свіжий запуск | 5 | 0 | 0 |
| Python scripts | 46 | 0 | 0 |
| app instrumented, Android 16/API 36 | 225 | 0 | 6 assumptions |
| FFmpeg instrumented, Android 16/API 36 | 15 | 0 | 0 |

Debug-набір виконувався і під час record, і окремо під час **verifyRoborazziDebug**; це повторна верифікація тих самих сценаріїв, не додаткові унікальні тести. У diff додано **116 параметризованих/звичайних test cases**. Матриця: чотири палітри, 320dp, font scale 100/130/150/200%, multiline content, targets, disabled, selected, DPAD, observer disposal та native view retention.

Release/Play unit tasks за чинним правилом проєкту виключають category `RequiresComposeTestManifest`; ці UI-сценарії виконані в debug + screenshot gate. Нічого не додано до exclusions.

Instrumented runner повідомив `OK (225 tests)`: за унікальним class/test ID **219 passed і 6 assumption skips**. Сценарії з пропусками залишилися незміненими: LAN television probe; real-TV playback acceptance; два temporary-playlist probes; два checks для Mi TV document stub. Їм потрібні явні opt-in runner arguments/відповідне TV-середовище. На фізичні пристрої APK не встановлювався. Створений ізольований `small_phone` 720×1280 був запущений headless та зупинений після тестів.

Python на Windows виконувався з bundled **3.12.14**. Початковий локальний 3.11 не підтримував `Path.is_junction`; джерела build-скрипта не змінювалися. Для shell checks процесний PATH спрямовано на Windows `rg.exe`, бо Git Bash вибирав однойменний Unix-бінарник із resources Codex; глобальний PATH не змінювався.

## Gates та збірка

- `recordRoborazziDebug` + debug/test APK build: **BUILD SUCCESSFUL**, 6m15s.
- `verifyRoborazziDebug`, release/Play unit та lint: **BUILD SUCCESSFUL**, 11m44s.
- Окремий свіжий FFmpeg release unit: **BUILD SUCCESSFUL**, 15s.
- app lint debug/release/Play: **0 errors, 0 warnings**, по 2 informational hints — `OldTargetApi`, `GradleDependency` для Compose BOM. Target SDK і залежності в цьому етапі не змінювалися.
- app detekt: **0 findings**; core detekt пройшов через UP-TO-DATE. Baseline залишився порожнім.
- Architecture boundaries і documentation references: OK.
- Shell contract suites: baseline 8 cases, documentation references 5, instrumented runner 12, Cast icon placement 8 — OK.

Зібрано universal debug APK для трьох чинних ABI: `app/build/outputs/apk/debug/app-universal-debug.apk`.

- Application ID: `com.uacastplayer.debug`.
- Size: **39 657 223 bytes**.
- SHA-256: **C59AB9DECD3B1F277D10AC0574431832E4BAFA439F3F3D0F3C80B89E16748BCD**.
- У DEX додатково перевірена наявність нових `UaSemanticColors`, `ControlFeedbackKt`, `SelectionSurfacesKt`, `SegmentedControlKt`: APK містить поточну реалізацію, а не стару збірку.

Це debug APK; release для розповсюдження, публікація GitHub/Play і version bump не виконувалися.

## PNG та контроль змін

У screenshot-наборі 93 PNG: додано 6 каталогів/TV/large-text previews, оновлено 64 чинні еталони через спільні токени/компоненти. Після record виконаний окремий pixel verify. Вручну переглянуті шість нових PNG та репрезентативні playlist/player surfaces.

- `cinema_v2_components.png` — Cinema, 320dp.
- `cinema_v2_components_azure.png`, `cinema_v2_components_midnight.png`, `cinema_v2_components_daylight.png` — сумісність палітр.
- `cinema_v2_components_200.png` — 320dp, 200%, reduced effects.
- `cinema_v2_tv_focus.png` — TV focus/selected/disabled.

Докази й checkpoints: `build/cinema-v2-stage2-2026-11-13/`. Основні логи: `accepted-record-build.log`, `final-verify-variants.log`, `ffmpeg-release-fresh.log`, `app-instrumented.log`, `ffmpeg-instrumented.log`, `python-tests-runtime.log`. JUnit summary: `final-junit-summary.json`; унікальні native outcomes: `app-instrumented-outcomes.json`.

Остаточний scoped checkpoint: `after/manifest.json` і `after/changes.zip`. Source guard допускає зміни лише спільних UI/theme/components, TV focus, їхніх тестів, golden PNG та документації. Жоден початковий файл поза цими межами не втрачено й не змінено; HEAD/index збережені.

## Межі підтвердження

Фізичний Android TV та справжні Cast/DLNA receivers не перевірялися. Native-прогін виконано на Android 16; окремі API 24/30 device-прогони та зовнішній CI не запускалися. FPS/Macrobenchmark цього етапу не вимірювався — є структурні обмеження motion та автоматизовані lifecycle/geometry/contrast checks, а не нові числові performance claims.

Впровадження нового компонування головної/каталогу та окремий редизайн плеєра залишаються наступними етапами затвердженого плану. До етапу 3 потрібен окремий дозвіл.
