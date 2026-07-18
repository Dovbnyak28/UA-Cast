# Design system

Every screen is built from the tokens in `ui/theme/` and the components in `ui/components/`
(mainly `DesignSystemControls.kt`). Vanilla Material3 widgets (`FilterChip`, `OutlinedButton`,
`MaterialTheme.typography.*`, `MaterialTheme.colorScheme.*`, `Toast`) should not appear in new UI
code - they don't track this palette/type scale and drift out of sync with the rest of the app the
moment a token changes. `TermsScreen`/`HelpScreen`/`PlayerScreen` are the reference implementations
for "screen built entirely from tokens."

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
