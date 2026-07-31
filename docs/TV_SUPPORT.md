# Android TV support (not yet implemented)

The manifest still declares `android.software.leanback` (`required=false`), a TV banner, and
`android.hardware.touchscreen required=false` - these are harmless to keep and save re-plumbing
later. The `LEANBACK_LAUNCHER` intent-filter category was removed, though: without it, the app
doesn't show up as launchable from a real TV home screen, which is correct today, because the UI
has no D-pad/focus navigation at all. Launching it on a TV would leave the user with no way to
move focus between channels, groups, or player controls.

## What's needed before re-adding `LEANBACK_LAUNCHER`

- **Focusable modifiers everywhere interactive.** Every clickable row, card, button, and icon
  needs `Modifier.focusable()` (or the D-pad-aware equivalents) plus explicit focus order where
  the default up/down/left/right inference gets it wrong (grids especially).
- **A focus-ring visual in the design system.** `ui/theme/` has press-scale and hairline-border
  tokens for touch; TV needs an equivalent "focused" visual state (e.g. an Azure focus ring or
  scale-up) applied consistently, the same way `PressScaleRound`/`Hairline` are applied today.
- **D-pad player controls instead of gestures.** `player/PlayerGesturePolicy.kt` assumes a
  touchscreen (drag zones, swipe thresholds, double-tap) - none of that is reachable via D-pad.
  The fullscreen player needs a D-pad-navigable control overlay as an alternative input path, not
  a replacement (touch still needs to keep working on touch-capable TVs/tablets).
- **Verification on a TV emulator**, since layouts and touch-target assumptions that look fine on
  a phone/tablet emulator commonly break focus traversal or overflow on a 10-foot UI.

Until the above lands, treat this app as phone/tablet-only.
