# Design system

Every screen is built from the tokens in `ui/theme/` and the components in `ui/components/`
(mainly `DesignSystemControls.kt`). Vanilla Material3 widgets (`FilterChip`, `OutlinedButton`,
`MaterialTheme.typography.*`, `MaterialTheme.colorScheme.*`, `Toast`) should not appear in new UI
code - they don't track this palette/type scale and drift out of sync with the rest of the app the
moment a token changes. `TermsScreen`/`HelpScreen`/`PlayerScreen` are the reference implementations
for "screen built entirely from tokens."

**Colors specifically go through `UaTheme.palette` (see "Themes" below), not a bare Color.kt
constant.** The color names below (`Void`, `Azure`, `LabelPrimary`, ...) are still where the
*values* live, but a composable reads them as `UaTheme.palette.void`, `UaTheme.palette.azure`,
`UaTheme.palette.labelPrimary`, etc. - lowerCamelCase, off the active palette - so the app has more
than one selectable look. Spacing/radii/typography/motion tokens are unaffected by this and stay
plain top-level constants.

## Color (`ui/theme/Color.kt`)

- **Backgrounds** - `Void` (app background), `VoidElevated` (~2.5% brighter, elevated surfaces),
  `Surface1`/`Surface2` (card and row backgrounds, `Surface2` is the "raised" one - selected chip,
  pressed round button, progress track).
- **Accent** - `Azure`/`Azure2`, and `AzureGradient` (linear gradient between them) for primary
  actions (play button, selected segment glow).
- **Route health semantics** - `RouteGreen`/`RouteAmber`/`RouteRed`, each with a matching `*Glow`
  color at low alpha for soft glows behind status dots.
- **Text** - `LabelPrimary` (main text), `LabelSecondary` (secondary/hint text, ~60% alpha),
  `LabelTertiary` (disabled, ~30% alpha).
- **Lines** - `Hairline`, a near-transparent white for 1dp borders/dividers.

## Spacing & shape (`ui/theme/Dimens.kt`)

- **Spacing** - `ScreenHPadding`/`CardPadding` (20dp), `ItemPadding` (14dp), `GapS`/`GapM`/`GapL`
  (8/14/20dp) for gaps between rows and sections.
- **Radii** - `RadiusList` (20), `RadiusCard` (24), `RadiusItem` (16, chips/small cards),
  `RadiusField` (14, text fields), `RadiusSeg`/`RadiusSegInner` (12/9, `SegmentedControl` shell and
  its sliding highlight).
- **Button sizes** - `PlayButtonSize` (66), `RoundButtonSize` (46), `IconButtonSize` (38),
  `TouchTargetMin` (48, minimum tappable area regardless of visual size).

## Typography (`ui/theme/Type.kt`)

All styles are tabular-figure `TextStyle`s (no `MaterialTheme.typography.*`):
`LargeTitle`/`Title` (screen/section headers), `CardTitle` (card headings), `BodyText` (semibold
body), `BodyRegular` (regular body/labels), `Caption`/`CaptionSemibold` (secondary text),
`Micro`/`SectionLabel`/`PillText`/`LiveText`/`RingValue`/`TabLabel` (small-scale specialty labels -
pill text, tab bar labels, health-ring numerals). Pick the closest semantic match rather than
reaching for `MaterialTheme.typography.bodyMedium` etc.

## Motion (`ui/theme/Motion.kt`)

- `EaseSpring` - the standard easing curve for all token-driven animations.
- `DurPress` (250ms) - press/release scale and highlight-slide animations.
- `DurEnter` (700ms), `DurRing` (1400ms), `StaggerMs`, `GlideMs`, `BreatheMs` - screen-entry and
  ambient animation timings.
- **Rule 2 (press-scale)** - interactive controls scale down slightly on press using
  `collectIsPressedAsState()` + `animateFloatAsState`: `PressScalePlay` (0.94, play button),
  `PressScaleRound` (0.88, round icon buttons), `PressScaleIcon` (0.90, small icon buttons). New
  pressable controls should follow the same pattern rather than relying on the default ripple.

## Components (`ui/components/DesignSystemControls.kt` unless noted)

- **§5.1 `GradientPlayButton`** - the main circular play/pause control (`AzureGradient` fill, glow
  shadow, `PressScalePlay`).
- **§5.2 `RoundIconButton`** - secondary round icon button (prev/next), `Surface1`/`Surface2`
  swap on press.
- **§5.3 `StatusPill`** - small rounded status label, colored by `StatusPillVariant`
  (Good/Proxy/Bad -> Route Green/Amber/Red).
- **§5.4 `SegmentedControl`** - equal-width segmented control with an animated sliding highlight.
  Use for a row of **at most 4 short, mutually-exclusive options** that comfortably fit the screen
  width (e.g. list density, buffer size). For more options, or options whose labels are long
  phrases, use a chip row instead (see `SettingsChip` below) - `SegmentedControl` degrades badly
  once labels start wrapping or eliding.
- **§5.5 `GlowStatusDot`** - a status dot with a soft glow, same `StatusPillVariant` palette as
  `StatusPill`.
- **§5.6 `TrackProgress`** - thin non-interactive progress track for list rows, plus a bold
  interactive variant for the player.
- **§5.7 `SecondaryButton`** - text button for secondary actions (export/import, "use suggested
  URL", "open" links). `Surface1`/`Surface2` background swap on press (same interaction pattern as
  `RoundIconButton`), `Hairline` border, `RadiusItem` corners, `BodyRegular` label in
  `LabelPrimary` (or `LabelTertiary` when `enabled = false`). Use this instead of Material's
  `OutlinedButton`.
- **§5.9 `TabBarLabel`** - tab bar label chrome shared by `GlassTabBar` items.
- **§5.10** - lives in `GlassTabBar.kt`; the glass-blur tab bar container itself.
- **§4 rule 3** - lives in `ui/player/PlayerScreen.kt`; the transient toast-style gesture indicator
  (fade in/out overlay) used for seek/volume/brightness/resize-mode feedback during playback. The
  reference pattern for any other "brief transient feedback over content" need.

### Banner pattern (`DownloadStatusBanner`, `IconTierBanner`)

Non-modal, dismissible status strips: a `Surface1`-to-`Surface2` vertical gradient background, 1dp
`Hairline` border, rounded corners, a title row (`TabLabel` style, `LabelPrimary`) with a small
`IconButton`+`Icons.Filled.Close` (`LabelSecondary` tint) to dismiss, and optional detail content
below. `DownloadStatusBanner` is pinned above the content with `statusBarsPadding()` and only
rounds its bottom corners (it sits flush against the top of the screen); `IconTierBanner` is
inline content with all four corners rounded. Reuse this pattern - rather than `Toast` or a new
one-off shape - for any other "explain transient/dismissible state inline" need.

### `SettingsChip`

A private composable in `ui/settings/SettingsScreen.kt`, not part of the shared catalog above
(same visual language, but settings-specific: it renders a leading checkmark when selected instead
of swapping label color). Built directly on `Box`/`Row`/`clickable` - `Surface2` background when
unselected, `Azure` when selected, `RadiusItem` corners, `BodyRegular` label. Used for chip rows
with more than 4 options or long labels (language, EPG source, icon display mode) where
`SegmentedControl` wouldn't fit.

## Themes (`ui/theme/UaPalette.kt`, `CinemaPalette.kt`, `MidnightPalette.kt`, `Theme.kt`, `Background.kt`)

The app has three selectable visual styles. Users pick one in Settings; it applies instantly,
app-wide.

| Theme | Background | Accent | Character |
| --- | --- | --- | --- |
| `AppTheme.AZURE` (default) | neutral near-black, textured | cool blue | unchanged from before themes existed |
| `AppTheme.CINEMA` | warm charcoal, textured | champagne gold | serif display type, pill-shaped controls |
| `AppTheme.MIDNIGHT` | true `#000000`, flat | muted pewter | no wallpaper texture, no vignette, maximum contrast |

They're deliberately spread across the axes rather than being three shades of the same idea: Azure
and Cinema differ in *temperature* while painting the same faint wallpaper texture a few percent
above black, so Midnight takes the axis both leave open - unlit, textureless, and the only one whose
`void` is actually black.

**Midnight's accent is deliberately near-neutral, and that is the theme's idea rather than a
compromise.** A saturated accent on true black is the loudest thing a phone screen can do: there is
no ambient tone for it to sit against, so it glows. The first attempt was a violet and read as
candy. At a fifth of normal saturation the chrome reads as chrome, and saturation belongs to the
only things that should compete for the eye - `routeGreen`, `routeAmber`, `routeRed`. In this theme
colour means status and nothing else, so a new element that wants attention has to earn it with
contrast or size rather than by turning the accent up.

### How it works

- **`UaPalette`** is a `data class` holding every semantic color plus a handful of per-theme
  shape/font toggles (`displayFontFamily`, `vignette`, `wallpaperTexture`, `pillButtons`,
  `secondaryButtonStyle`). `AzureUaPalette`, `CinemaUaPalette` and `MidnightUaPalette` are the
  concrete values - `AzureUaPalette` is built straight from the existing Color.kt constants, so
  Azure's rendering is byte-for-byte what it was before this system existed.
- **`wallpaperTexture = false`** makes `Background.kt` return a flat `void` fill and skip the
  gradient/noise layers entirely, rather than tinting them to nothing. That's what keeps Midnight's
  black actually `#000000` on an OLED panel: a texture drawn at 2% over black is still lit pixels.
- **`LocalUaPalette`** (a `staticCompositionLocalOf<UaPalette>`) carries the active palette down
  the tree; **`UaTheme.palette`** is the `@Composable` accessor components actually call.
  `staticCompositionLocalOf` is deliberate, not an oversight - a theme switch is meant to force the
  *entire* subtree to recompose immediately, which is the opposite of what `compositionLocalOf`'s
  finer-grained invalidation would give you.
- **`UaCastTheme(theme: AppTheme, content)`** (in `Theme.kt`) is the actual root: it picks the
  palette for `theme`, provides it via `LocalUaPalette`, and builds the Material3 `ColorScheme`
  from it too (so Material internals - ripples, `OutlinedTextField`, etc. - track the theme as
  well, even though new UI code shouldn't be reading `MaterialTheme.colorScheme.*` directly).
- **`AppPreferences.appTheme`** (default `AppTheme.AZURE`) persists the choice.
  `AppViewModel.selectAppTheme` writes it and updates `AppUiState.appTheme`; `MainActivity` passes
  that straight into `UaCastTheme(theme = uiState.appTheme)`, so picking a theme in Settings
  recomposes the whole app on the spot - no restart.

### Rules for adding a new theme

1. Add the enum case to `AppTheme`.
2. Write a new `UaPalette` value (see `CinemaPalette.kt` for the shape: a handful of `private val`
   base colors, then the full `UaPalette(...)` constructor call). Every field needs a real value -
   there's no "inherit from Azure" shortcut, so a half-finished theme won't compile.
3. Wire it into `UaCastTheme`'s `when (theme)` branch in `Theme.kt`.
4. Add its name string to all 4 locales and a `SegmentedControl` option in Settings' theme picker
   (`SettingsScreen.kt`) if it should be user-selectable yet.
5. **Never introduce a `Color(0x...)` literal or a hardcoded shape/font choice inside a screen or
   component to special-case the new theme.** If an existing screen needs a color the palette
   doesn't have yet, add the field to `UaPalette` (with a value for *every* existing theme) rather
   than branching on `UaTheme.palette == CinemaUaPalette` or similar at the call site - components
   read the palette, they don't know which theme they're in.
6. `scripts/check-no-hardcoded-colors.sh` (run in CI) fails the build on any `Color(0x...)` under
   `ui/` outside `ui/theme/` - that's the enforcement mechanism for rule 5.
   `scripts/check-glow-not-text.sh` is the other half: a `*Glow` field is a ~50%-alpha fill, and a
   new theme's background is exactly what turns "a bit dim" into "fails contrast" (see the doc on
   `UaPalette.azureGlow`).
7. Record goldens for it: a `captureThemed` case in `DesignSystemScreenshotTest`, and - if the theme
   changes anything structural rather than only hues, as `wallpaperTexture` does - a populated one
   in `HomeDashboardScreenshotTest`, where an empty screen would show nothing.
8. Re-record `ThemePickerScreenshotTest`. **Every theme added narrows the theme picker**: its
   `SegmentedControl` gives each segment `weight(1f)`, and a weight decides how much room a segment
   gets - it cannot create a place to wrap. A name that outgrows its share doesn't truncate, it goes
   two lines tall and takes the whole control with it. Check all four locales; the longest name is
   rarely the English one.
9. Compute the contrast ratios rather than eyeballing them. Body text on `void`/`surface1` should
   clear WCAG AA (4.5:1), large display type and icons 3:1. Record the numbers in a comment next to
   the color, as `CinemaPalette.kt` and `MidnightPalette.kt` do - the next person changing a hue by
   "just a little" needs to know what the margin was.

### Small (<12sp) accent text

`UaPalette.accentText` exists because a theme's accent hue can look thin/washed-out as small plain
text (badges, "view all" links) even when it reads fine on icons or larger text - Cinema's gold is
the motivating case, see `UaPalette.kt`'s doc comment on the field. Icon tints and larger accent
text keep using `UaPalette.azure`; only Caption/Micro-scale text runs should use `accentText`.

## §D Depth (`ui/theme/Depth.kt`)

Flat, single-color surfaces read as cheap - a few subtle "raised"/"sunken" treatments break that up
without turning into a full skeuomorphic style.

- **Tokens** - `UaPalette.edgeHighlightNeutral`/`edgeHighlightStrong`/`edgeHighlightAccent` (border
  tones for [raisedSurface]), `shadowSoft` (ambient/spot shadow color), `surfaceLiftAmount` (how
  much lighter/darker the gradient's far edge gets, as a [lighten]/[darken] fraction). Cinema's
  edges are ivory/gold-tinted rather than pure white, matching its warm palette - see
  `CinemaPalette.kt`.
- **`Modifier.raisedSurface(shape, base, edgeColor, shadow)`** - a top-to-bottom gradient from a
  lightened `base` to `base`, plus a thin edge-highlight border. `shadow` defaults to `false`.
- **`Modifier.sunkenSurface(shape, base)`** - the inverse (darkened-to-base gradient), no border, no
  shadow ever - used for recessed input chrome (text fields).
- **`lighten(Color, fraction)`/`darken(Color, fraction)`** - pure color-blend functions (no
  Compose/Android dependency), unit-tested at the 0f/1f/out-of-range boundaries in `DepthTest.kt`.

### Performance rules

- Never allocate a `Brush`/`Paint` directly in a composable body on every recomposition -
  `raisedSurface`/`sunkenSurface` cache theirs via `remember(base, liftAmount)`, rebuilding only
  when the surface's own color actually changes, not on every recomposition.
- **`shadow = true` is forbidden on anything inside a `LazyColumn`/`LazyGrid` item** - a per-row
  shadow re-triggers layer compositing on every scroll frame for every visible row. Use
  `raisedSurface(shadow = false)` (the default) for list rows; the gradient/border alone is cheap.

### The three-glow rule

Only three places in the app may use an **accent-colored** glow (`spotColor`/`ambientColor` beyond
`UaPalette.shadowSoft`'s neutral tone): the play button (`GradientPlayButton`, `azureGlow`), the
current-programme progress indicator, and the live indicator. Nowhere else - a glow on every raised
surface reads as visual noise instead of drawing the eye to what's actually live/actionable.
`raisedSurface` itself never glows for this reason; a glowing control layers its own
`.shadow(spotColor = ...)` separately, the same way `GradientPlayButton` already does.

### Serif display type

`UaPalette.displayFontFamily` (`FontFamily.Serif` in Cinema, the platform default elsewhere) is
consumed through `Type.kt`'s `DisplayTitle`/`DisplayName` styles, not read directly at call sites -
see the "Typography" section above for the base styles they wrap. `FontFamily.Serif` is Android's
generic serif alias (resolves to whatever serif face the device ships) rather than a specific
bundled typeface like Playfair Display: no font binary was available when this was built, and the
first attempt (Android's Downloadable Fonts API against the Google Play Services Fonts provider)
was confirmed on-device to silently fail on de-Googled ROMs (LineageOS + microG) - package-visibility
blocked the query since microG doesn't implement that provider, so it fell back to the platform
default with no error. `FontFamily.Serif` has none of that risk (no network, no GMS dependency) at
the cost of not being a specific named typeface. A future iteration could swap in an actually-bundled
OFL font file by changing just `CinemaPalette.kt`'s `displayFontFamily` value.

## §E Equal-share rows

A `Row` of labeled icon buttons whose item count can vary at runtime (a conditional item like
`PlayerScreen`'s "previous channel" quick-setting, only shown once `PlayerUiState.hasPreviousChannel`
is true) must give every item `Modifier.weight(1f)`. Without it, `Arrangement.SpaceEvenly` only
affects *positioning*, not the width each child is measured with - each `Column` sizes to its label's
intrinsic width, and once the row runs out of room the last items get squeezed into whatever space is
left, wrapping their `Text` character-by-character instead of onto a clean second line. Pair
`weight(1f)` with `textAlign = TextAlign.Center` and `maxLines = 2` on the label so a two-word label
wraps cleanly rather than clipping or overflowing. This was found on `PlayerScreen`'s
`QuickSettingsRow` going from 5 to 6 items; the same risk applies to any other variable-count icon
row built the same way.

**Equal shares are necessary but not sufficient - the real constraint is the longest single word.**
An earlier version of this section cited `"Співвідношення"` as an example of a label that wraps
cleanly under these rules. It does not, and the app shipped it: at 14 characters it is one word with
no break point, so a sixth of the row width leaves Compose nothing to do but break it mid-word, and
the Ukrainian player read `Співвідно / шення`. Weights control how much room each item *gets*; they
cannot create a place to wrap. So when adding an item to a row like this, check every locale for the
longest **word**, not the longest string - and where a term is unavoidably long, translate it as two
short words instead of one long one (`"Формат кадру"`, not `"Співвідношення"`). Roughly: keep the
longest word under about 9 characters for a six-item row at 411dp.
