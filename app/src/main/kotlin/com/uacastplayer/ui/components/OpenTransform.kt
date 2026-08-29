package com.uacastplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.uacastplayer.ui.theme.DUR_ENTER
import com.uacastplayer.ui.theme.EaseSpring

/** How small the screen starts. Small enough to read as opening outward, large enough that nothing
 * about the layout looks like it was ever a different size. */
private const val OPEN_START_SCALE = 0.93f

/** Vertical anchor: the video sits at the top of the player, so growing from the upper third reads
 * as the picture opening out rather than the whole page inflating from its middle. */
private const val OPEN_ORIGIN_Y = 0.28f

/**
 * Scales and fades a screen in as it opens over what the user was looking at.
 *
 * Deliberately *not* a shared-element flight of the channel logo from its tile, which is the more
 * obvious way to spend this budget. Two reasons. A shared element needs the player composed inside
 * an `AnimatedVisibility`, and `PlayerHost` owns the ExoPlayer instance - creating decoders while an
 * enter animation runs puts the two most expensive things in the app in the same 300ms. And a logo
 * flying across a screen that otherwise hard-cuts looks worse than no transition at all: the eye
 * follows the one moving thing and reads everything around it as a jump.
 *
 * Growing the whole surface instead gives the same "I opened *this*" reading with one animated
 * property and no lifecycle change. It is safe here specifically because the video renders into a
 * `texture_view` (see res/layout/player_view.xml) - a `SurfaceView` is composited by separate
 * hardware and would tear away from a scaling parent.
 *
 * Runs once, keyed on [key]: switching channels inside an already-open player must not replay it.
 */
@Composable
fun Modifier.openTransform(key: Any?): Modifier {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(DUR_ENTER, easing = EaseSpring)) }

    return this.graphicsLayer {
        val value = progress.value
        alpha = value
        scaleX = OPEN_START_SCALE + (1f - OPEN_START_SCALE) * value
        scaleY = scaleX
        transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = OPEN_ORIGIN_Y)
    }
}
