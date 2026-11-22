# Cinema V2 — дизайн-система Compose, етап 2

Це спільні токени й компоненти. Екрани, маршрути, Media3, Cast/DLNA та бізнес-логіка не переробляються. Каталог компонентів не є новим екраном застосунку. Етап 3 потребує окремого дозволу.

## Кольори та сумісність тем

`UaPalette` і стабільні ID `AZURE`, `CINEMA`, `MIDNIGHT`, `DAYLIGHT`, `SYSTEM` збережені. Cinema отримала послідовний шампансько-золотий градієнт замість золотого з кораловим. Сімейства Azure, Midnight та Daylight залишилися своїми. SYSTEM використовує чинний resolver; темна палітра відеоконтролів у світлому режимі також збережена.

`SemanticColors.kt` перетворює активну палітру на повний `ColorScheme`, включно з fixed/inverse/error ролями. Результат обчислюється через `remember(palette)`, а не окремо в кожній композиції елемента. `UaTheme.colors` містить Material-схему, success/warning/danger та focus-ролі. Це доповнює сумісний `UaTheme.palette`, не змінюючи його конструктори.

| Роль | Використання |
|---|---|
| primary / onPrimary | Основна дія з контрастним текстом |
| primaryContainer / onPrimaryContainer | Спокійне тональне виділення, відмінне від primary |
| secondary/tertiary та їх container/on* | Допоміжні Material-компоненти без стандартного фіолетового fallback |
| surfaceContainerLowest / Low / regular / High / Highest | Рівні поверхонь від void до surface2 |
| outline | Непрозора контрастна обводка інтерактивного поля |
| outlineVariant | Декоративні роздільники й межі карток |
| success / warning / danger | Тональний контейнер, читабельний onContainer та колір статусу |
| focusRing / focusSeparator | Золотий/брендовий фокус із роздільником, видимий також на заповненій primary-кнопці |

Нормальний текст перевіряється на 4.5:1, інтерактивні обводки й фокус — на 3:1. Disabled-стан позначено також у semantics. Вибір списку має галочку, кнопки — роль Button, сегменти — single-choice RadioButton/selected semantics. Колір не є єдиним повідомленням стану.

## Типографіка й ритм

Усі 15 Material typography roles мають явні offline SansSerif family та line height. Нові display-ролі — 36/44, 32/40, 28/36 sp; заголовки та labels утворюють послідовну ієрархію. Чинні іменовані стилі залишилися доступними. Для `bodyLarge` збережено `BodyText` 14.5/21 sp: перевірка 240dp landscape при 200% тексту показала, що глобальне збільшення до 16/24 забирає місце у чинної шторки каналів.

Нові компоненти використовують `AppSpacing`: 4/8/12/16/24/32 dp. Старі `ScreenHPadding`, `Gap*`, навігаційні метрики й відступи екранів залишаються сумісними. Шрифти задано в sp; компоненти ростуть за висотою, а не зменшують системний font scale.

## Компоненти

- `PrimaryButton`, `SecondaryButton`: мінімальна висота 48dp, multiline labels, іконка, enabled/disabled, Button semantics і TV-фокус. Основний підпис — `ButtonLabel`.
- `RoundIconButton`, `SmallRoundIconButton`: власні фізичні 48×48dp, без перетину розширених невидимих targets. Play-кнопка зберігає 66dp.
- `UaCard`: тональна Surface, довільний ColumnScope content, необов'язкова дія, selected/enabled. Без тіней на кожному елементі списку.
- `UaListItem`: заголовок, supporting text, необов'язкова іконка, selected/enabled та callback. Текст переноситься; вибраний рядок має галочку.
- `SegmentedControl`: чинний API з options/index/callback. Доки підписи вміщаються, зберігається горизонтальний sliding highlight. Якщо виміряні підписи не вміщаються, використовується повноширинний single-choice stack. Це не обрізає й не розбиває слова по літерах при 320dp/200%.
- `StatusPill` та `uaTextFieldColors` використовують семантичні foreground/container/outline, не декоративний glow як колір тексту.

Приклад використання:

```kotlin
ZorikTheme(theme) {
    UaCard(selected = selected, onClick = onSelect) {
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
    UaListItem(headline = name, supportingText = description,
        enabled = available, onClick = onOpen)
}
```

Компоненти не створюють власників відтворення, сховищ чи мережевих запитів. Callback залишається відповідальністю наявного екрана/контролера.

## Motion та Android TV

Натискання використовує 120ms easing і масштаб 0.97/0.96 лише під час малювання. Геометрія click/focus/semantics не змінюється. Немає нового постійного animation clock, blur чи відеопрев'ю. App reduced effects та системний animator scale 0 вимикають цей рух; вибір і натискання продовжують працювати.

Один lifecycle-owned ContentObserver на корені `ZorikTheme` обслуговує всі вкладені компоненти, включно з `UaPlayerTheme`; після видалення композиції він знімається. Для standalone-компонентів без кореневої теми зберігається локальний fallback. Додавання motion-provider не замінює native child при перемиканні теми.

TV-фокус малюється 3dp обводкою поверх 6dp роздільника. Він додається до наявного clickable/selectable target, а не створює ще один focusable. DPAD пропускає disabled-елементи; OK викликає callback один раз. Чинні TV-навігація, density policy та registry не змінені.

## Перевірки й прев'ю

Контракти перевіряють чотири палітри, 320dp та 100/130/150/200% тексту, реальні межі натискання й рядків тексту, вибір, disabled, DPAD, observer disposal та збереження native child. `hasVisualOverflow` не використовується як єдина перевірка auto-width Text: paragraph може бути ширшим за tight layout без обрізаних літер. Перевіряються фактичні line bounds, висота, відсутність ellipsis та пропущених рядків.

Реальні native Compose PNG: `app/src/test/screenshots/cinema_v2_components*.png` і `cinema_v2_tv_focus.png`. Каталог доступний також через Android Studio `@Preview`. Звіт про фактично виконані gates зберігається окремо у `archive/CINEMA_V2_STAGE2_VERIFICATION_2026-11-13.md`.

Довідка: [Material 3 ColorScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/ColorScheme), [Compose MotionDurationScale](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale). API звірено також із зафіксованим Material3 1.4.0 у локальному кеші; залежності не оновлювалися.
