package app.zorik.player.ui.guidedtour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.zorik.player.guidedtour.GuidedTip
import app.zorik.player.guidedtour.GuidedTourPhase
import app.zorik.player.guidedtour.GuidedTourState
import app.zorik.player.ui.theme.DUR_PRESS
import app.zorik.player.ui.theme.EaseSpring
import app.zorik.player.ui.theme.GapL
import app.zorik.player.ui.theme.LocalReducedEffects
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.UaTheme

/** How much of the screen the scrim takes. Dark enough that the hole reads as the only lit thing,
 * light enough that the user can still tell which screen they are on - the point of highlighting a
 * live element rather than showing a picture of one. */
private const val SCRIM_ALPHA = 0.7f

/** Breathing room around the highlighted element, so the hole does not clip its own touch target. */
private val SpotlightPadding = 8.dp
private val SpotlightCorner = 16.dp

/** The ring that pulses around the hole, so the eye finds it before it reads the card. */
private val RingWidth = 2.dp
private val RingReach = 10.dp
private const val RING_PERIOD_MILLIS = 1400

/** Keeps the card readable on a tablet, where full width would be one line stretched across ten
 * inches. */
private val CardMaxWidth = 420.dp

/**
 * The intro or a tip, drawn over the running app.
 *
 * Composed as the last sibling of the app content so it covers everything, and it consumes every
 * touch that reaches it - the highlighted element is *shown*, not made tappable. Letting taps through
 * was tempting and wrong: the app would move on under a card that still describes what was there.
 *
 * Renders nothing at all when idle, which is its state for all but a few seconds of the app's life.
 */
@Composable
fun GuidedTourOverlay(
    state: GuidedTourState,
    callbacks: GuidedTourCallbacks,
    modifier: Modifier = Modifier,
    hasChannels: Boolean = false,
) {
    // The last thing shown, kept for the length of the fade-out: without it the content would switch
    // to "nothing" on the first frame of the exit and the fade would animate an empty box.
    val lastShown = remember { arrayOfNulls<GuidedTourState>(1) }
    if (state.isVisible) lastShown[0] = state
    val shown = if (state.isVisible) state else lastShown[0]

    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(DUR_PRESS, easing = EaseSpring)),
        exit = fadeOut(tween(DUR_PRESS, easing = EaseSpring)),
        modifier = modifier,
    ) {
        // Back never falls through to the app underneath: with the app covered, a back press that
        // switched tabs behind the scrim would be both invisible and unexplained. Gated on isVisible
        // because this content stays composed for the fade-out, and an un-gated handler would
        // swallow the back press of someone who closed the card and immediately pressed back.
        BackHandler(enabled = state.isVisible) {
            when {
                state.phase == GuidedTourPhase.TIP -> callbacks.onDismissTip()
                state.introPage > 0 -> callbacks.onIntroBack()
                else -> callbacks.onFinishIntro()
            }
        }
        when (shown?.phase) {
            GuidedTourPhase.INTRO -> IntroScreen(state = shown, hasChannels = hasChannels, callbacks = callbacks)
            GuidedTourPhase.TIP -> shown.tip?.let { TipOverlay(tip = it, onDismiss = callbacks.onDismissTip) }
            GuidedTourPhase.IDLE, null -> Unit
        }
    }
}

/**
 * The dimmed screen with a hole over the tip's element, and the tip's card in the other half.
 *
 * The dim with its hole is one very wide stroke around a rounded rectangle - see [drawScrimAround].
 * Not an even-odd path and not a layer cleared with `BlendMode.Clear`: both cost real time on every
 * frame, and the player page under the player tips redraws every frame while video plays. Measured
 * on a Mi A2 (Snapdragon 660, minified build): the path version took the page from a median 11 ms
 * frame to 29 ms, every frame janky, for as long as a tip was up.
 *
 * A tap anywhere dismisses, like "Got it": a tip is a hint, not a gate, and making someone find the
 * one button that closes it would turn it into one.
 */
@Composable
private fun TipOverlay(tip: GuidedTip, onDismiss: () -> Unit) {
    val registry = LocalGuidedTourTargets.current
    // Null while the element is momentarily gone (a recomposition that moves it); the card then
    // stays centred under an even dim instead of disappearing.
    val spotlight = registry.boundsOf(tip.targetKey)?.takeUnless { it.isEmpty }
    val holeAlpha by animateFloatAsState(
        targetValue = if (spotlight == null) 0f else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "guidedTipSpotlight",
    )
    val pulse = rememberRingPulse()
    val palette = UaTheme.palette
    val scrimColor = palette.void.copy(alpha = SCRIM_ALPHA)
    val density = LocalDensity.current
    val padPx = with(density) { SpotlightPadding.toPx() }
    val cornerPx = with(density) { SpotlightCorner.toPx() }
    val ringWidthPx = with(density) { RingWidth.toPx() }
    val ringReachPx = with(density) { RingReach.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
            // One traversal group, so TalkBack reads the card as a unit instead of interleaving it
            // with whatever is behind the scrim.
            .semantics { isTraversalGroup = true },
    ) {
        val rootHeightPx = constraints.maxHeight.toFloat()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hole = spotlight?.takeIf { holeAlpha > 0f }
            if (hole == null) {
                drawRect(color = scrimColor)
                return@Canvas
            }
            val inflated = hole.inflate(padPx)
            drawScrimAround(inflated, cornerPx, scrimColor)
            val reach = ringReachPx * pulse.value
            val ring = inflated.inflate(reach)
            drawRoundRect(
                color = palette.azure.copy(alpha = holeAlpha * (1f - pulse.value)),
                topLeft = ring.topLeft,
                size = Size(ring.width, ring.height),
                cornerRadius = CornerRadius(cornerPx + reach, cornerPx + reach),
                style = Stroke(width = ringWidthPx),
            )
        }
        TipCardSlot(alignment = cardAlignment(spotlight, rootHeightPx)) {
            GuidedTipCard(tip = tip, onDismiss = onDismiss)
        }
    }
}

/**
 * Dims everything outside [hole], leaving it clear with corners of [cornerPx].
 *
 * Drawn as a single stroke of a rounded rectangle, centred half a stroke outside the hole: the
 * stroke's inner edge is then exactly the hole (a rounded rectangle shrunk by half the stroke keeps
 * radius [cornerPx]) and its outer edge lies beyond every corner of the screen. A stroked rounded
 * rectangle is one of the shapes the GPU renderer draws analytically, where an arbitrary path has to
 * be tessellated again each frame.
 */
private fun DrawScope.drawScrimAround(hole: Rect, cornerPx: Float, color: Color) {
    // Twice the longer side always exceeds the screen diagonal, so the outer edge covers every pixel
    // whichever corner of the screen the hole sits in.
    val stroke = 2f * maxOf(size.width, size.height)
    val half = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(hole.left - half, hole.top - half),
        size = Size(hole.width + stroke, hole.height + stroke),
        cornerRadius = CornerRadius(cornerPx + half, cornerPx + half),
        style = Stroke(width = stroke),
    )
}

/** 0 -> 1 on a loop, or a fixed 0 with reduced effects: the ring then sits still on the hole's
 * edge, which still marks the element without moving anything. */
@Composable
private fun rememberRingPulse(): State<Float> {
    if (LocalReducedEffects.current) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "guidedTipRing")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RING_PERIOD_MILLIS, easing = EaseSpring), RepeatMode.Restart),
        label = "guidedTipRingPulse",
    )
}

@Composable
private fun BoxScope.TipCardSlot(alignment: Alignment, card: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .safeDrawingPadding()
            .padding(horizontal = ScreenHPadding, vertical = GapL),
    ) {
        Box(Modifier.align(alignment).widthIn(max = CardMaxWidth).fillMaxWidth()) { card() }
    }
}

/**
 * Which half of the screen the card goes in: the one the highlighted element is not in.
 *
 * Derived from the element's measured bounds, never from a hardcoded position - the only thing this
 * layout needs to know about a target is which side of the middle it landed on.
 */
internal fun cardAlignment(spotlight: Rect?, rootHeightPx: Float): Alignment = when {
    spotlight == null -> Alignment.Center
    spotlight.center.y < rootHeightPx / 2f -> Alignment.BottomCenter
    else -> Alignment.TopCenter
}
