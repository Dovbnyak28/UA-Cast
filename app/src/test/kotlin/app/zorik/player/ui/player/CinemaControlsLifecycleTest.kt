package app.zorik.player.ui.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.zorik.player.testing.RequiresComposeTestManifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class CinemaControlsLifecycleTest {
    @get:Rule val rule = createComposeRule()
    private val state = PlayerScreenTransientState(null, null)
    private val mounted = mutableStateOf(true)
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun backgroundCancelsTheDeadlineAndResumeStartsAFullInterval() {
        mount()
        rule.mainClock.advanceTimeBy(2_000)
        change { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle { assertTrue(state.controlsVisible) }
        change { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { assertTrue(state.controlsVisible) }
        rule.mainClock.advanceTimeBy(1_200)
        rule.runOnIdle { assertFalse(state.controlsVisible) }
    }

    @Test fun disposalCannotDeliverAStaleHideCallback() {
        mount()
        rule.mainClock.advanceTimeBy(2_000)
        change { mounted.value = false }
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle { assertTrue(state.controlsVisible) }
    }

    @Test fun activeLevelGestureHoldsThePanel() {
        mount()
        change { state.showGesture(GestureIndicatorKind.VOLUME) }
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle { assertTrue(state.controlsVisible) }
        change { state.gestureIndicator = null }
        rule.mainClock.advanceTimeBy(3_200)
        rule.runOnIdle { assertFalse(state.controlsVisible) }
    }

    private fun mount() {
        rule.mainClock.autoAdvance = false
        rule.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            if (mounted.value) PlayerControlsAutoHide(state, true)
        } }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun change(block: () -> Unit) {
        rule.runOnIdle { block(); Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }
}
