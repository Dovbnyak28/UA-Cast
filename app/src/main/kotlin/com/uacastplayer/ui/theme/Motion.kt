package com.uacastplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

private const val SPRING_CONTROL_POINT_LOW = 0.2f
private const val SPRING_CONTROL_POINT_HIGH = 0.8f

val EaseSpring = CubicBezierEasing(
    SPRING_CONTROL_POINT_LOW,
    SPRING_CONTROL_POINT_HIGH,
    SPRING_CONTROL_POINT_LOW,
    1f,
)

const val DUR_ENTER = 700
/** Duration for subtle top-level destination transitions. */
const val DUR_NAV = 220
const val DUR_PRESS = 250
const val DUR_RING = 1400
const val STAGGER_MS = 70
const val GLIDE_MS = 2200
const val BREATHE_MS = 2000

// Press-scale targets (rule 2)
const val PRESS_SCALE_PLAY = 0.94f
const val PRESS_SCALE_ROUND = 0.88f
const val PRESS_SCALE_ICON = 0.90f

/** Unified pressed-state darkening (rule 2 extension, see ui/theme/Depth.kt's [darken]) - how much
 * darker a [com.uacastplayer.ui.theme.raisedSurface]'s base color gets while pressed, instead of
 * each control picking its own ad-hoc pressed color. */
const val PRESSED_DARKEN_FRACTION = 0.10f
