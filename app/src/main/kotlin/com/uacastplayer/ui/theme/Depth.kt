package com.uacastplayer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val RaisedShadowElevation = 4.dp

/**
 * Blends [color] toward white by [fraction] (0f..1f, clamped), preserving alpha. Pure - no
 * Compose/Android dependency, so it's directly unit-testable.
 */
fun lighten(color: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return color.copy(
        red = color.red + (1f - color.red) * f,
        green = color.green + (1f - color.green) * f,
        blue = color.blue + (1f - color.blue) * f,
    )
}

/**
 * Blends [color] toward black by [fraction] (0f..1f, clamped), preserving alpha. Pure - no
 * Compose/Android dependency, so it's directly unit-testable.
 */
fun darken(color: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return color.copy(
        red = color.red * (1f - f),
        green = color.green * (1f - f),
        blue = color.blue * (1f - f),
    )
}

/**
 * A subtly "raised" surface: a top-to-bottom gradient from a lightened [base] to [base] itself
 * (see [UaPalette.surfaceLiftAmount]), plus a thin edge highlight border. Set [shadow] = true only
 * outside scrolling lists - a per-item shadow inside a `LazyColumn`/`LazyGrid` re-triggers layer
 * compositing on every scroll frame for every visible row, which is the actual cost this rule is
 * guarding against (a raised gradient/border alone is cheap; a shadow layer isn't).
 *
 * Only three places in the app may additionally glow (accent shadow/spotColor beyond this soft
 * neutral shadow): the play button, the current-programme progress indicator, and the live
 * indicator - see docs/DESIGN_SYSTEM.md "§D Depth". This modifier itself never glows; a glowing
 * control layers its own `.shadow(spotColor = ...)` on top separately (see GradientPlayButton).
 */
@Composable
fun Modifier.raisedSurface(
    shape: Shape,
    base: Color,
    edgeColor: Color = UaTheme.palette.edgeHighlightNeutral,
    shadow: Boolean = false,
): Modifier {
    val liftAmount = UaTheme.palette.surfaceLiftAmount
    val shadowColor = UaTheme.palette.shadowSoft
    val brush = remember(base, liftAmount) {
        Brush.verticalGradient(listOf(lighten(base, liftAmount), base))
    }
    return this
        .let { modifier ->
            if (shadow) {
                modifier.shadow(RaisedShadowElevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
            } else {
                modifier
            }
        }
        .clip(shape)
        .background(brush)
        .border(1.dp, edgeColor, shape)
}

/**
 * A subtly "sunken" surface: a top-to-bottom gradient from a darkened [base] to [base] itself -
 * the visual inverse of [raisedSurface]. Never shadowed (a sunken surface reads as *receding*, a
 * shadow around it would read as raised instead) and has no edge highlight border for the same
 * reason. Use for text field backgrounds and other "recessed input" chrome.
 */
@Composable
fun Modifier.sunkenSurface(shape: Shape, base: Color): Modifier {
    val liftAmount = UaTheme.palette.surfaceLiftAmount
    val brush = remember(base, liftAmount) {
        Brush.verticalGradient(listOf(darken(base, liftAmount), base))
    }
    return this.clip(shape).background(brush)
}
