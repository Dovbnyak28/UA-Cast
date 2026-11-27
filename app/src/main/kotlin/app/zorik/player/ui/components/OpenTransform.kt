package app.zorik.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import app.zorik.player.ui.theme.DUR_ENTER
import app.zorik.player.ui.theme.EaseSpring

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
 * property and no lifecycle change. The player's video is a `SurfaceView` (see
 * res/layout/player_view.xml), which cannot follow this layer: use [rememberOpening] there, so the
 * surface is covered by [coverWhileOpening] until the layer is gone.
 *
 * Runs once, keyed on [key]: switching channels inside an already-open player must not replay it.
 * Disabled/completed motion returns the caller's modifier without a graphics layer. Turning
 * motion back on must not hide a surface that is already visible.
 */
@Composable
fun Modifier.openTransform(key: Any?, onOpacityChanged: (Boolean) -> Unit = {}): Modifier {
    val enabled = animationsAllowed()
    var complete by remember(key) { mutableStateOf(!enabled) }
    val notifyOpacity by rememberUpdatedState(onOpacityChanged)
    // The navigation underlay may stop painting only after this actual transform is opaque.
    // A separate wall-clock timer can finish before an animation's first delayed frame.
    SideEffect { notifyOpacity(!enabled || complete) }
    DisposableEffect(Unit) { onDispose { notifyOpacity(false) } }
    LaunchedEffect(key, enabled) {
        if (!enabled) complete = true
    }
    if (!enabled || complete) return this
    return animatedOpenTransform(key) { complete = true }
}

/**
 * Read at draw time: true while an enclosing [openTransform] still scales and fades its screen.
 *
 * A `SurfaceView` is not drawn into that graphics layer. SurfaceFlinger composites it as its own
 * layer behind the window, seen through a transparent hole the view punches into the window. While
 * the opening's layer is translucent the hole shows whatever lies under the player instead of the
 * video, and the surface follows the scale a frame behind the page around it. Nothing reads the
 * value during composition, so the end of an opening redraws only the covered surfaces.
 */
val LocalOpeningInProgress = staticCompositionLocalOf<() -> Boolean> { { false } }

/** [openTransform]'s modifier plus the [LocalOpeningInProgress] value for the screen it opens. */
class Opening internal constructor(val modifier: Modifier, val inProgress: () -> Boolean)

/**
 * [openTransform] for a screen that hosts a `SurfaceView`: provide [Opening.inProgress] as
 * [LocalOpeningInProgress] around the screen [Opening.modifier] is applied to.
 */
@Composable
fun rememberOpening(key: Any?, onOpacityChanged: (Boolean) -> Unit = {}): Opening {
    val opaque = remember { mutableStateOf(false) }
    val inProgress = remember(opaque) { { !opaque.value } }
    val modifier = Modifier.openTransform(key) { isOpaque ->
        // Written in openTransform's SideEffect, before this frame draws: an opening with motion
        // disabled never paints its cover, not even for one frame.
        opaque.value = isOpaque
        onOpacityChanged(isOpaque)
    }
    return Opening(modifier, inProgress)
}

/**
 * Paints [color] over this node's content - a native video surface - while an opening is in
 * progress (see [LocalOpeningInProgress]). A channel's first frame almost never arrives within
 * [DUR_ENTER], so the box looks exactly like the black shutter the player would show anyway.
 */
@Composable
fun Modifier.coverWhileOpening(color: Color = Color.Black): Modifier {
    val inProgress = LocalOpeningInProgress.current
    return drawWithContent {
        drawContent()
        if (inProgress()) drawRect(color)
    }
}

@Composable
private fun Modifier.animatedOpenTransform(key: Any?, onComplete: () -> Unit): Modifier {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.animateTo(1f, tween(DUR_ENTER, easing = EaseSpring))
        onComplete()
    }

    return this.graphicsLayer {
        val value = progress.value
        alpha = value
        scaleX = OPEN_START_SCALE + (1f - OPEN_START_SCALE) * value
        scaleY = scaleX
        transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = OPEN_ORIGIN_Y)
    }
}
