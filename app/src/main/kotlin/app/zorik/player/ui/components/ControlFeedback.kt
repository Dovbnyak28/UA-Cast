package app.zorik.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import app.zorik.player.ui.theme.DUR_FEEDBACK
import app.zorik.player.ui.theme.EaseSpring
import app.zorik.player.ui.theme.motionAllowed

internal fun controlScaleTarget(pressed: Boolean, enabled: Boolean, motion: Boolean, scale: Float): Float =
    if (pressed && enabled && motion) scale else 1f

/** Draw-only feedback: the 48dp click/focus/semantics bounds never shrink or move. */
@Composable
internal fun Modifier.controlFeedback(pressed: Boolean, pressedScale: Float, enabled: Boolean = true): Modifier {
    val motion = motionAllowed()
    val animated = animateFloatAsState(controlScaleTarget(pressed, enabled, motion, pressedScale),
        tween(if (motion) DUR_FEEDBACK else 0, easing = EaseSpring), label = "controlFeedback")
    return drawWithContent { scale(animated.value) { this@drawWithContent.drawContent() } }
}
