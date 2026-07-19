package com.uacastplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

val EaseSpring = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

const val DurEnter = 700
const val DurPress = 250
const val DurRing = 1400
const val StaggerMs = 70
const val GlideMs = 2200
const val BreatheMs = 2000

// Press-scale targets (rule 2)
const val PressScalePlay = 0.94f
const val PressScaleRound = 0.88f
const val PressScaleIcon = 0.90f

/** Unified pressed-state darkening (rule 2 extension, see ui/theme/Depth.kt's [darken]) - how much
 * darker a [com.uacastplayer.ui.theme.raisedSurface]'s base color gets while pressed, instead of
 * each control picking its own ad-hoc pressed color. */
const val PressedDarkenFraction = 0.10f
