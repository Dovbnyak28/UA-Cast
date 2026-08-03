package com.uacastplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.uacastplayer.ui.theme.DurRing

/** How far past the button's own edge the ring travels before it has fully faded. */
private const val RING_MAX_GROWTH = 0.42f

/** Opacity at the moment the ring leaves the button's edge; it fades to nothing from there. */
private const val RING_START_ALPHA = 0.55f

private val RingStrokeWidth = 1.5.dp

/**
 * A slow ring that leaves a circular button and fades, while a cast session is live.
 *
 * The state it shows had exactly one channel before this: the icon's tint. That is legible when you
 * already know to look, and invisible when you don't - a blue glyph and a white glyph next to each
 * other read as two buttons, not as one connected and one not. Motion is the difference a user
 * notices without being told what to compare against.
 *
 * Drawn *behind* the button and outside its bounds, so it never touches the glyph or the button's
 * own surface. When [active] is false nothing is drawn and no animation runs at all - this sits on
 * a screen that is playing video, and an idle infinite transition is a frame's worth of work every
 * frame for something nobody can see.
 */
@Composable
fun Modifier.liveRing(active: Boolean, color: Color): Modifier {
    if (!active) return this

    val transition = rememberInfiniteTransition(label = "liveRing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DurRing, easing = LinearEasing)),
        label = "liveRingPulse",
    )
    val stroke = with(LocalDensity.current) { RingStrokeWidth.toPx() }

    return this.drawBehind {
        val baseRadius = size.minDimension / 2f
        drawCircle(
            color = color.copy(alpha = RING_START_ALPHA * (1f - progress)),
            radius = baseRadius * (1f + RING_MAX_GROWTH * progress),
            style = Stroke(width = stroke),
        )
    }
}
