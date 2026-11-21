package com.uacastplayer.ui.guidedtour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uacastplayer.guidedtour.GuidedTourPhase
import com.uacastplayer.guidedtour.GuidedTourState
import com.uacastplayer.guidedtour.GuidedTourStep
import com.uacastplayer.guidedtour.GuidedTourTarget
import com.uacastplayer.guidedtour.TooltipPosition
import com.uacastplayer.log.AppLog
import com.uacastplayer.ui.theme.DUR_PRESS
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme
import kotlinx.coroutines.delay

private const val TAG = "GuidedTour"

/** How much of the screen the scrim takes. Dark enough that the hole reads as the only lit thing,
 * light enough that the user can still tell which screen they are on - the point of highlighting a
 * live element rather than showing a picture of one. */
private const val SCRIM_ALPHA = 0.68f

/** Breathing room around the highlighted element, so the hole does not clip the edge of its own
 * touch target. */
private val SpotlightPadding = 8.dp
private val SpotlightCorner = 14.dp

/** Keeps the card readable on a tablet, where full width would be one line of text stretched across
 * ten inches. */
private val CardMaxWidth = 420.dp

/** Long enough to outlast the bottom-tab Crossfade (300ms, see `RootScaffold`) plus a frame or two
 * of layout, so a target that is merely still arriving is not reported as missing. */
private const val TARGET_SETTLE_MILLIS = 450L

/**
 * The tour, drawn over the running app.
 *
 * Composed as the last sibling of the app content so it covers everything, and it consumes every
 * touch that reaches it - the highlighted element is *shown*, not made tappable. Letting taps
 * through was tempting and wrong: the app would navigate somewhere the tour did not expect, and the
 * next step would point at an element that is no longer there.
 *
 * Renders nothing at all when the tour is idle, which is its state for all but a few seconds of the
 * app's life.
 */
@Composable
fun GuidedTourOverlay(
    state: GuidedTourState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onAddPlaylist: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(DUR_PRESS, easing = EaseSpring)),
        exit = fadeOut(tween(DUR_PRESS, easing = EaseSpring)),
        modifier = modifier,
    ) {
        // Back leaves from the welcome card and steps backwards everywhere else. It never falls
        // through to the app underneath: with the app covered, a back press that switched tabs
        // behind the scrim would be both invisible and unexplained.
        //
        // Gated on isVisible rather than left always-on, because this content stays composed for
        // the length of the fade-out - and an un-gated handler would swallow the back press of
        // someone who closed the tour and immediately pressed back.
        BackHandler(enabled = state.isVisible) {
            if (state.phase == GuidedTourPhase.WELCOME) onSkip() else onBack()
        }
        GuidedTourScrim(state = state) { spotlight, rootHeightPx ->
            GuidedTourCard(
                state = state,
                spotlight = spotlight,
                rootHeightPx = rootHeightPx,
                onNext = onNext,
                onBack = onBack,
                onSkip = onSkip,
                onAddPlaylist = onAddPlaylist,
                onComplete = onComplete,
            )
        }
    }
}

/**
 * The dimmed screen with a hole in it.
 *
 * The hole is one even-odd path rather than a layer cleared with `BlendMode.Clear`, which needs its
 * own offscreen compositing layer - a cost paid on every frame, on the low-end devices this app
 * explicitly supports.
 */
@Composable
private fun GuidedTourScrim(
    state: GuidedTourState,
    content: @Composable BoxScope.(spotlight: Rect?, rootHeightPx: Float) -> Unit,
) {
    val registry = LocalGuidedTourTargets.current
    val step = state.currentStep
    val spotlight = step?.let { resolveSpotlight(it, registry) }
    val scrimColor = UaTheme.palette.void.copy(alpha = SCRIM_ALPHA)
    val density = LocalDensity.current
    val padPx = with(density) { SpotlightPadding.toPx() }
    val cornerPx = with(density) { SpotlightCorner.toPx() }
    // Fades the hole in rather than snapping it. A step with no target keeps a plain, even dim.
    val holeAlpha by animateFloatAsState(
        targetValue = if (spotlight == null) 0f else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "guidedTourSpotlight",
    )

    // Keyed on the step and on whether it resolved, so this says its piece once per step rather
    // than on every recomposition - and says it at all, which is what separates "designed as text"
    // from "the key was renamed and nobody noticed".
    //
    // The delay is what makes it worth reading. A step that switches tabs has no target on its
    // first frame by definition - the destination screen has not been composed yet, and the tab
    // Crossfade takes 300ms - so logging immediately reported a fallback for every step that
    // navigates, including the ones that then highlighted correctly a moment later. Waiting past
    // that leaves only the steps that really had nothing to point at.
    LaunchedEffect(step?.id, spotlight == null) {
        val target = step?.target as? GuidedTourTarget.Element ?: return@LaunchedEffect
        if (spotlight != null) return@LaunchedEffect
        delay(TARGET_SETTLE_MILLIS)
        AppLog.d(TAG) { "step '${step.id}' target '${target.key}' is not on screen; falling back" }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Swallows taps, drags and everything else. Without it the user is operating a live UI
            // they cannot see.
            .pointerInput(Unit) { detectTapGestures { } }
            // One traversal group, so TalkBack reads the card as a unit instead of interleaving it
            // with whatever is behind the scrim.
            .semantics { isTraversalGroup = true },
    ) {
        val rootHeightPx = constraints.maxHeight.toFloat()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hole = spotlight?.takeIf { holeAlpha > 0f }
            if (hole == null) {
                drawRect(color = scrimColor)
            } else {
                val inflated = Rect(
                    left = hole.left - padPx,
                    top = hole.top - padPx,
                    right = hole.right + padPx,
                    bottom = hole.bottom + padPx,
                )
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(inflated, CornerRadius(cornerPx, cornerPx)))
                }
                drawPath(path = path, color = scrimColor)
            }
        }
        content(spotlight, rootHeightPx)
    }
}

/**
 * The step's target, or null when there is nothing to highlight - either because the step never had
 * an element, or because that element is not composed right now.
 */
private fun resolveSpotlight(step: GuidedTourStep, registry: GuidedTourTargetRegistry): Rect? {
    val target = step.target as? GuidedTourTarget.Element ?: return null
    // A zero-area element is on screen only in the arithmetic sense; a hole around it would be a
    // dot hovering over nothing.
    return registry.boundsOf(target.key)?.takeIf { it.width > 0f && it.height > 0f }
}

@Composable
private fun BoxScope.GuidedTourCard(
    state: GuidedTourState,
    spotlight: Rect?,
    rootHeightPx: Float,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onAddPlaylist: () -> Unit,
    onComplete: () -> Unit,
) {
    val alignment = cardAlignment(
        position = state.currentStep?.position ?: TooltipPosition.AUTO,
        spotlight = spotlight,
        rootHeightPx = rootHeightPx,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = ScreenHPadding, vertical = GapL),
    ) {
        Column(
            modifier = Modifier
                .align(alignment)
                .widthIn(max = CardMaxWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusCard))
                .background(UaTheme.palette.surface2)
                .border(1.dp, UaTheme.palette.hairline, RoundedCornerShape(RadiusCard))
                .padding(GapL),
            verticalArrangement = Arrangement.spacedBy(GapM),
        ) {
            key(state.phase, state.currentStep) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GapM),
                ) {
                    when (state.phase) {
                        GuidedTourPhase.WELCOME -> WelcomeContent(onSkip)
                        GuidedTourPhase.STEPS -> state.currentStep?.let { StepContent(state, it) }
                        GuidedTourPhase.DONE -> DoneContent()
                        GuidedTourPhase.IDLE -> Unit
                    }
                }
            }
            GuidedTourActions(state.phase, onNext, onBack, onSkip, onAddPlaylist, onComplete)
        }
    }
}


/**
 * Which half of the screen the card goes in: the one the highlighted element is not in.
 *
 * Derived from the element's measured bounds, never from a hardcoded position - the only thing this
 * layout needs to know about a target is which side of the middle it landed on.
 */
private fun cardAlignment(position: TooltipPosition, spotlight: Rect?, rootHeightPx: Float): Alignment {
    if (spotlight == null) return Alignment.Center
    return when (position) {
        TooltipPosition.ABOVE -> Alignment.TopCenter
        TooltipPosition.BELOW -> Alignment.BottomCenter
        TooltipPosition.AUTO ->
            if (spotlight.center.y < rootHeightPx / 2f) Alignment.BottomCenter else Alignment.TopCenter
    }
}
