package com.uacastplayer.ui.guidedtour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.guidedtour.GuidedTourPhase
import com.uacastplayer.guidedtour.GuidedTourState
import com.uacastplayer.guidedtour.GuidedTourStep
import com.uacastplayer.guidedtour.GuidedTourTarget
import com.uacastplayer.guidedtour.TooltipPosition
import com.uacastplayer.log.AppLog
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.DurPress
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GapS
import com.uacastplayer.ui.theme.LargeTitle
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme
import kotlinx.coroutines.delay

private const val TAG = "GuidedTour"

/** How much of the screen the scrim takes. Dark enough that the hole reads as the only lit thing,
 * light enough that the user can still tell which screen they are on - the point of highlighting a
 * live element rather than showing a picture of one. */
private const val SCRIM_ALPHA = 0.82f

/** Breathing room around the highlighted element, so the hole does not clip the edge of its own
 * touch target. */
private val SpotlightPadding = 8.dp
private val SpotlightCorner = 14.dp

/** Material's default button height is under the 48dp minimum touch target, and these are the only
 * controls reachable while the tour is up. */
private val ActionMinHeight = 48.dp

private val DotSize = 8.dp
private val ScreenshotMaxHeight = 220.dp

/** Keeps the card readable on a tablet, where full width would be one line of text stretched across
 * ten inches. */
private val CardMaxWidth = 420.dp

/**
 * Above this font scale the three step actions stack instead of sharing a row.
 *
 * 1.5 and 2.0 are where `FontScaleLayoutTest` measured the row overflowing a 4.5" viewport; 1.3
 * still fits. The threshold sits between them rather than at 1.5 so a device whose Display-size
 * setting narrows the screen as well has a little room before it breaks.
 */
private const val STACKED_ACTIONS_FONT_SCALE = 1.4f

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
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(DurPress, easing = EaseSpring)),
        exit = fadeOut(tween(DurPress, easing = EaseSpring)),
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
        animationSpec = tween(DurPress, easing = EaseSpring),
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
            when (state.phase) {
                GuidedTourPhase.WELCOME -> WelcomeContent(onNext = onNext, onSkip = onSkip)
                GuidedTourPhase.STEPS -> state.currentStep?.let { step ->
                    StepContent(state = state, step = step, onNext = onNext, onBack = onBack, onSkip = onSkip)
                }
                GuidedTourPhase.DONE -> DoneContent(onComplete = onComplete)
                GuidedTourPhase.IDLE -> Unit
            }
        }
    }
}

@Composable
private fun WelcomeContent(onNext: () -> Unit, onSkip: () -> Unit) {
    Text(
        text = stringResource(R.string.guided_tour_welcome_title),
        style = LargeTitle,
        color = UaTheme.palette.labelPrimary,
    )
    Text(
        text = stringResource(R.string.guided_tour_welcome_body),
        style = BodyText,
        color = UaTheme.palette.labelSecondary,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = ActionMinHeight)) {
            Text(stringResource(R.string.guided_tour_skip), color = UaTheme.palette.labelSecondary)
        }
        Box(modifier = Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.heightIn(min = ActionMinHeight)) {
            Text(stringResource(R.string.guided_tour_start))
        }
    }
}

@Composable
private fun StepContent(
    state: GuidedTourState,
    step: GuidedTourStep,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    StepProgress(current = state.stepNumber, total = state.stepCount)
    StepIllustration(step = step)
    Text(
        text = stringResource(step.titleRes),
        style = CardTitle,
        color = UaTheme.palette.labelPrimary,
    )
    Text(
        text = stringResource(step.descriptionRes),
        style = BodyText,
        color = UaTheme.palette.labelSecondary,
    )
    StepActions(onNext = onNext, onBack = onBack, onSkip = onSkip)
}

/**
 * Skip, Back and Next - side by side normally, stacked when the text is too big for one line.
 *
 * The stacked branch is not defensive programming; `FontScaleLayoutTest` caught the row running off
 * a 4.5" screen at 1.5x and 2.0x, which is a real Display-size setting and not an exotic one. Three
 * labels plus their padding simply do not fit in the card's inner width there, and the item that
 * left the screen was Next - the one control the step exists to offer.
 *
 * Stacked, the order flips to importance-first (Next, Back, Skip) rather than keeping the
 * horizontal layout's left-to-right reading, because vertically the top item is the primary one.
 */
@Composable
private fun StepActions(onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    val skipLabel = stringResource(R.string.guided_tour_skip)
    val backLabel = stringResource(R.string.guided_tour_back)
    val nextLabel = stringResource(R.string.guided_tour_next)

    if (LocalDensity.current.fontScale >= STACKED_ACTIONS_FONT_SCALE) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GapS),
        ) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) { Text(nextLabel) }
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) { Text(backLabel, color = UaTheme.palette.azure) }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
            ) { Text(skipLabel, color = UaTheme.palette.labelSecondary) }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = ActionMinHeight)) {
            Text(skipLabel, color = UaTheme.palette.labelSecondary)
        }
        Box(modifier = Modifier.weight(1f))
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = ActionMinHeight)) {
            Text(backLabel, color = UaTheme.palette.azure)
        }
        Button(onClick = onNext, modifier = Modifier.heightIn(min = ActionMinHeight)) {
            Text(nextLabel)
        }
    }
}

/**
 * The step's picture, when it has one.
 *
 * Composed only for the step being shown, so exactly one drawable is ever decoded - which is the
 * reason a screenshot is a resource id on the step rather than an entry in a preloaded list. A step
 * whose element *is* on screen shows nothing here: the live highlight already is the illustration.
 */
@Composable
private fun StepIllustration(step: GuidedTourStep) {
    val registry = LocalGuidedTourTargets.current
    val resource = when (val target = step.target) {
        is GuidedTourTarget.Screenshot -> target.resource
        is GuidedTourTarget.Element ->
            if (registry.boundsOf(target.key) == null) target.fallbackScreenshot else null
        GuidedTourTarget.None -> null
    } ?: return

    Image(
        painter = painterResource(resource),
        contentDescription = stringResource(step.titleRes),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ScreenshotMaxHeight)
            .clip(RoundedCornerShape(RadiusItem)),
    )
}

@Composable
private fun DoneContent(onComplete: () -> Unit) {
    Text(
        text = stringResource(R.string.guided_tour_done_title),
        style = LargeTitle,
        color = UaTheme.palette.labelPrimary,
    )
    Text(
        text = stringResource(R.string.guided_tour_done_body),
        style = BodyText,
        color = UaTheme.palette.labelSecondary,
    )
    Button(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth().heightIn(min = ActionMinHeight),
    ) {
        Text(stringResource(R.string.guided_tour_finish))
    }
}

/**
 * Dots plus "3 / 7".
 *
 * Both, not one: the dots give the shape of the thing at a glance, the numbers answer "how much is
 * left" without counting them. The whole row is a single accessibility node reading the numbers -
 * seven unlabelled dots announced one at a time would be noise.
 */
@Composable
private fun StepProgress(current: Int, total: Int) {
    val label = stringResource(R.string.guided_tour_progress, current, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .size(DotSize)
                        .clip(CircleShape)
                        .background(if (index < current) UaTheme.palette.azure else UaTheme.palette.hairline),
                )
            }
        }
        Box(modifier = Modifier.weight(1f))
        Text(text = label, style = Caption, color = UaTheme.palette.labelTertiary)
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
