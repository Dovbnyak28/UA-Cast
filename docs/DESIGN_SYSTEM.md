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

## Themes (`ui/theme/UaPalette.kt`, `CinemaPalette.kt`, `Theme.kt`, `Background.kt`)

The app has two selectable visual styles, `AppTheme.AZURE` (default, unchanged from before themes
existed) and `AppTheme.CINEMA` (warm charcoal background, champagne-gold accent, serif display
type, pill-shaped controls). Users pick one in Settings; it applies instantly, app-wide.

### How it works

- **`UaPalette`** is a `data class` holding every semantic color plus a handful of per-theme
  shape/font toggles (`displayFontFamily`, `vignette`, `pillButtons`, `secondaryButtonStyle`).
  `AzureUaPalette` and `CinemaUaPalette` are the two concrete values - `AzureUaPalette` is built
  straight from the existing Color.kt constants, so Azure's rendering is byte-for-byte what it was
  before this system existed.
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

### Small (<12sp) accent text

`UaPalette.accentText` exists because a theme's accent hue can look thin/washed-out as small plain
text (badges, "view all" links) even when it reads fine on icons or larger text - Cinema's gold is
the motivating case, see `UaPalette.kt`'s doc comment on the field. Icon tints and larger accent
text keep using `UaPalette.azure`; only Caption/Micro-scale text runs should use `accentText`.

### Serif display type

`UaPalette.displayFontFamily` (serif in Cinema via Playfair Display, platform default elsewhere) is
consumed through `Type.kt`'s `DisplayTitle`/`DisplayName` styles, not read directly at call sites -
see the "Typography" section above for the base styles they wrap. Cinema's font is fetched via
Android's Downloadable Fonts API (Google Play Services Fonts provider, see
`res/font/playfair_display*.xml`) rather than bundled as a binary asset, since no real font binary
was available when this was built - it falls back silently to the platform default on devices
without Google Play Services. A future iteration could replace this with an actually-bundled OFL
font file without touching anything outside those two `res/font/*.xml` resources and
`CinemaPalette.kt`'s `CinemaDisplayFontFamily`.
