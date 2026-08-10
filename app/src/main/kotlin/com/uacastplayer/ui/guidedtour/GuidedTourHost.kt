package com.uacastplayer.ui.guidedtour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.uacastplayer.guidedtour.GuidedTourState

/**
 * Wraps the app in the tour: provides the target registry to everything inside [content], and draws
 * [GuidedTourOverlay] on top of it.
 *
 * One host per Activity. The registry is created here and never replaced, which is what lets
 * [LocalGuidedTourTargets] be a *static* composition local - reading it costs nothing and never
 * invalidates a reader.
 */
@Composable
fun GuidedTourHost(
    state: GuidedTourState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val registry = remember { GuidedTourTargetRegistry() }
    // Turning collection on is what makes every guidedTourTarget() in the tree start reporting; off
    // again the moment the tour closes, so an app that never opens it never runs a single extra
    // layout callback.
    LaunchedEffect(state.isVisible) { registry.isCollecting = state.isVisible }

    CompositionLocalProvider(LocalGuidedTourTargets provides registry) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = if (state.isVisible) {
                    // The accessibility half of "the overlay consumes every touch". Without it
                    // TalkBack still walks the app behind an opaque scrim, reading controls the
                    // user cannot see and activating ones the tour did not expect. The highlighted
                    // element goes quiet along with everything else - the card names it, and the
                    // card is what the user is being read.
                    Modifier.fillMaxSize().clearAndSetSemantics { }
                } else {
                    Modifier.fillMaxSize()
                },
            ) {
                content()
            }
            GuidedTourOverlay(
                state = state,
                onNext = onNext,
                onBack = onBack,
                onSkip = onSkip,
                onComplete = onComplete,
            )
        }
    }
}
