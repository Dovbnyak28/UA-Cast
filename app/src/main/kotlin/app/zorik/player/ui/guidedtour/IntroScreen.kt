package app.zorik.player.ui.guidedtour

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.guidedtour.GuidedTourState
import app.zorik.player.guidedtour.IntroPage
import app.zorik.player.ui.components.PrimaryButton
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.DisplayTitle
import app.zorik.player.ui.theme.GapL
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.GapS
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.TouchTargetMin
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.theme.appBackground

/** The picture's share of the page height, and the bounds it is kept within. */
private const val ART_HEIGHT_FRACTION = 0.46f
private const val SIDE_ART_HEIGHT_FRACTION = 0.8f
private val ArtMin = 112.dp
private val ArtMax = 280.dp

/** Wider than this many times its height, a page puts the picture beside the text - a phone held
 * sideways has no room for both stacked. */
private const val SIDE_BY_SIDE_RATIO = 1.3f

private val TextMaxWidth = 440.dp
private val ActionsMaxWidth = 420.dp

private val DotSize = 8.dp
private val ActiveDotWidth = 24.dp

/**
 * The first-launch intro: three illustrated slides, swipeable, with Skip on every slide but the
 * last and the real first step - Add playlist - as the last slide's button.
 *
 * Opaque, unlike a tip: on first launch there is nothing behind it worth seeing (an empty Home), and
 * a full page is what lets the pictures carry the explanation.
 *
 * The pager and the controller each hold a page number; the controller's is the source of truth for
 * the buttons, the pager's for the swipe. They meet in two effects: a settled swipe is reported up,
 * and a button press scrolls the pager to the new page.
 */
@Composable
internal fun IntroScreen(state: GuidedTourState, hasChannels: Boolean, callbacks: GuidedTourCallbacks) {
    val pageCount = state.pages.size
    val pagerState = rememberPagerState(initialPage = state.introPage) { pageCount }
    val onSettled by rememberUpdatedState(callbacks.onIntroPageSettled)
    LaunchedEffect(pagerState) { snapshotFlow { pagerState.settledPage }.collect { onSettled(it) } }
    LaunchedEffect(state.introPage) {
        if (pagerState.currentPage != state.introPage) pagerState.animateScrollToPage(state.introPage)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .appBackground()
            // Taps that miss every control stop here rather than reaching the app underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { isTraversalGroup = true },
    ) {
        val wide = maxWidth > maxHeight * SIDE_BY_SIDE_RATIO
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = GapL),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IntroTopBar(showSkip = !state.isLastIntroPage, onSkip = callbacks.onFinishIntro)
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                IntroPageContent(page = state.pages[index], sideBySide = wide)
            }
            PageDots(current = pagerState.currentPage, count = pageCount)
            Spacer(Modifier.height(GapL))
            IntroActions(
                isLastPage = state.isLastIntroPage,
                hasChannels = hasChannels,
                inRow = wide,
                callbacks = callbacks,
            )
        }
    }
}

@Composable
private fun IntroTopBar(showSkip: Boolean, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargetMin).padding(horizontal = GapS),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSkip) {
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = TouchTargetMin)) {
                Text(stringResource(R.string.guided_tour_skip), color = UaTheme.palette.labelSecondary)
            }
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage, sideBySide: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = ScreenHPadding)) {
        val share = if (sideBySide) SIDE_ART_HEIGHT_FRACTION else ART_HEIGHT_FRACTION
        val art: Dp = (maxHeight * share).coerceIn(ArtMin, ArtMax)
        if (sideBySide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(GapL, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IntroIllustration(page, Modifier.size(art))
                IntroText(
                    page = page,
                    align = TextAlign.Start,
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = TextMaxWidth)
                        .verticalScroll(rememberScrollState()),
                )
            }
        } else {
            // Scrolls at large font sizes; centred while it fits, which the min height provides -
            // a scrolling column on its own would pin everything to the top.
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IntroIllustration(page, Modifier.size(art))
                Spacer(Modifier.height(GapL))
                IntroText(page = page, align = TextAlign.Center, modifier = Modifier.widthIn(max = TextMaxWidth))
            }
        }
    }
}

@Composable
private fun IntroText(page: IntroPage, align: TextAlign, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GapM)) {
        Text(
            text = stringResource(page.titleRes),
            style = DisplayTitle,
            color = UaTheme.palette.labelPrimary,
            textAlign = align,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(page.bodyRes),
            style = BodyText,
            color = UaTheme.palette.labelSecondary,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Where the user is among the slides: the current one a wider accent pill. One accessibility node
 * reading "2 / 3" - three unlabelled dots announced one at a time would be noise.
 */
@Composable
private fun PageDots(current: Int, count: Int) {
    val label = stringResource(R.string.guided_tour_progress, current + 1, count)
    Row(
        modifier = Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) ActiveDotWidth else DotSize, label = "introDotWidth")
            val color by animateColorAsState(
                if (active) UaTheme.palette.azure else UaTheme.palette.labelTertiary,
                label = "introDotColor",
            )
            Box(Modifier.width(width).height(DotSize).clip(CircleShape).background(color))
        }
    }
}

/**
 * Next on every slide but the last. On the last: Add playlist and Later for someone with no
 * channels - the one thing they have to do next - or a single Start for someone who opened the intro
 * again from Settings and already has them.
 */
@Composable
private fun IntroActions(isLastPage: Boolean, hasChannels: Boolean, inRow: Boolean, callbacks: GuidedTourCallbacks) {
    val primaryLabel = when {
        !isLastPage -> stringResource(R.string.guided_tour_next)
        hasChannels -> stringResource(R.string.guided_tour_finish)
        else -> stringResource(R.string.guided_tour_add_playlist)
    }
    val onPrimary = when {
        !isLastPage -> callbacks.onIntroNext
        hasChannels -> callbacks.onFinishIntro
        else -> callbacks.onAddPlaylist
    }
    val showLater = isLastPage && !hasChannels
    val modifier = Modifier.widthIn(max = ActionsMaxWidth).fillMaxWidth().padding(horizontal = ScreenHPadding)
    if (inRow) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(GapM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showLater) LaterButton(callbacks.onFinishIntro, Modifier.weight(1f))
            PrimaryButton(text = primaryLabel, onClick = onPrimary, modifier = Modifier.weight(1f))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(GapS)) {
            PrimaryButton(text = primaryLabel, onClick = onPrimary, modifier = Modifier.fillMaxWidth())
            // Reserved even when absent, so the dots and the main button do not jump on the last slide.
            if (showLater) {
                LaterButton(callbacks.onFinishIntro, Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(TouchTargetMin))
            }
        }
    }
}

@Composable
private fun LaterButton(onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = TouchTargetMin)) {
        Text(stringResource(R.string.intro_later), color = UaTheme.palette.labelSecondary)
    }
}
