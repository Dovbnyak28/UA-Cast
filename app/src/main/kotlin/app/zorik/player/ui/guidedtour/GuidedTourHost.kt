package app.zorik.player.ui.guidedtour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.zorik.player.guidedtour.GuidedTip
import app.zorik.player.guidedtour.GuidedTourState
import kotlinx.coroutines.delay

/**
 * How long a tip's element has to stay put before the tip shows. Outlasts the tab Crossfade
 * (300ms) and the player's open animation, so the spotlight lands where the element ended up rather
 * than where it was mid-flight - and a screen the user only passed through never raises a tip.
 */
private const val TIP_SETTLE_MILLIS = 700L

/** What the intro and the tips can ask the app to do. One holder instead of seven parameters. */
@Immutable
data class GuidedTourCallbacks(
    val onIntroNext: () -> Unit = {},
    val onIntroBack: () -> Unit = {},
    val onIntroPageSettled: (Int) -> Unit = {},
    val onFinishIntro: () -> Unit = {},
    val onAddPlaylist: () -> Unit = {},
    val onShowTip: (GuidedTip) -> Unit = {},
    val onDismissTip: () -> Unit = {},
)

/**
 * When a tip may appear.
 *
 * @param pending the tips not seen yet, in priority order.
 * @param enabled false while something else owns the screen - a dialog, the add-playlist screen, a
 *   television - so a tip never lands on top of a decision the user is in the middle of.
 * @param playerExpanded whether the player page is what the user is looking at. Tips are matched to
 *   it by [GuidedTip.inPlayer].
 */
@Immutable
data class GuidedTipConditions(
    val pending: List<GuidedTip> = emptyList(),
    val enabled: Boolean = false,
    val playerExpanded: Boolean = false,
)

/**
 * Wraps the app in the guidance layer: provides the target registry to everything inside [content],
 * decides when a tip shows, and draws [GuidedTourOverlay] on top.
 *
 * One host per Activity. The registry is created here and never replaced, which is what lets
 * [LocalGuidedTourTargets] be a *static* composition local.
 */
@Composable
fun GuidedTourHost(
    state: GuidedTourState,
    callbacks: GuidedTourCallbacks,
    modifier: Modifier = Modifier,
    tips: GuidedTipConditions = GuidedTipConditions(),
    hasChannels: Boolean = false,
    content: @Composable () -> Unit,
) {
    val registry = remember { GuidedTourTargetRegistry() }
    // Measuring stays on while any tip is still to come - including while tips are blocked by a
    // dialog, so an element that arrived behind it still counts as new once the dialog closes - and
    // goes off for good once the last one has been seen.
    val collecting = state.isVisible || tips.pending.isNotEmpty()
    LaunchedEffect(collecting) { registry.isCollecting = collecting }

    // How far along the arrivals the user has already been shown something. A tip only shows for an
    // element that arrived after this - see GuidedTourTargetRegistry.
    var shownThrough by remember { mutableIntStateOf(0) }
    val currentState by rememberUpdatedState(state)
    val currentTips by rememberUpdatedState(tips)
    // derivedStateOf, because the registry's bounds change on every scroll frame and this only needs
    // to recompose when the answer does.
    val candidate by remember(registry) {
        derivedStateOf { nextTip(currentState, currentTips, registry, shownThrough) }
    }
    val onShowTip by rememberUpdatedState(callbacks.onShowTip)
    LaunchedEffect(candidate) {
        val tip = candidate ?: return@LaunchedEffect
        delay(TIP_SETTLE_MILLIS)
        onShowTip(tip)
    }
    val overlayCallbacks = remember(callbacks, registry) {
        callbacks.copy(onDismissTip = {
            shownThrough = registry.latestAppearance
            callbacks.onDismissTip()
        })
    }

    CompositionLocalProvider(LocalGuidedTourTargets provides registry) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = if (state.isVisible) {
                    // The accessibility half of "the overlay consumes every touch". Without it
                    // TalkBack still walks the app behind the scrim, reading controls the user
                    // cannot reach. The card names what it points at, and the card is what the user
                    // is being read.
                    Modifier.fillMaxSize().clearAndSetSemantics { }
                } else {
                    Modifier.fillMaxSize()
                },
            ) {
                content()
            }
            GuidedTourOverlay(state = state, callbacks = overlayCallbacks, hasChannels = hasChannels)
        }
    }
}

/** The first pending tip whose element is on the surface the user is looking at, has a size, and
 * arrived after the last thing they were shown. */
internal fun nextTip(
    state: GuidedTourState,
    tips: GuidedTipConditions,
    registry: GuidedTourTargetRegistry,
    shownThrough: Int,
): GuidedTip? {
    if (state.isVisible || !tips.enabled) return null
    return tips.pending.firstOrNull { tip ->
        val arrived = registry.appearanceOf(tip.targetKey) ?: return@firstOrNull false
        val bounds = registry.boundsOf(tip.targetKey) ?: return@firstOrNull false
        // A zero-area element is on screen only in the arithmetic sense.
        tip.inPlayer == tips.playerExpanded && arrived > shownThrough && !bounds.isEmpty
    }
}
