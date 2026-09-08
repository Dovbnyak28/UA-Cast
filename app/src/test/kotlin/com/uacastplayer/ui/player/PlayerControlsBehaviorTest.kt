package com.uacastplayer.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.rememberArtworkTone
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w785dp-h393dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlayerControlsBehaviorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `touch exploration never auto hides controls`() {
        val manager = accessibilityManager()
        val previous = manager.isTouchExplorationEnabled
        shadowOf(manager).setTouchExplorationEnabled(true)
        try {
            val state = startAutoHide()
            rule.mainClock.advanceTimeBy(60_000)
            rule.runOnIdle { assertTrue(state.controlsVisible) }
        } finally {
            shadowOf(manager).setTouchExplorationEnabled(previous)
        }
    }

    @Test fun `Android accessibility timeout extends the idle interval`() {
        val manager = accessibilityManager()
        shadowOf(manager).setInteractiveUiTimeout(10_000)
        try {
            val state = startAutoHide()
            rule.mainClock.advanceTimeBy(8_000)
            rule.runOnIdle { assertTrue(state.controlsVisible) }
            rule.mainClock.advanceTimeBy(3_000)
            rule.runOnIdle { assertFalse(state.controlsVisible) }
        } finally {
            shadowOf(manager).setInteractiveUiTimeout(0)
        }
    }

    private fun accessibilityManager(): AccessibilityManager =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    @Test fun `buffering shows Pause and unavailable navigation is disabled`() {
        var toggles = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.fillMaxSize()) {
                    PlayerControlsOverlay(
                        uiState = PlayerUiState(wantsToPlay = true, canControlPlayback = true),
                        isFullscreen = true,
                        sleepTimerRemainingMillis = remember { mutableStateOf(null) },
                        onExit = {}, onPlayPause = { toggles++ }, onNext = {}, onPrevious = {},
                        onToggleFullscreen = {}, onEnterPip = {}, onOpenSleepTimer = {},
                        isDlnaCasting = false, onOpenDlnaSheet = {}, onSelectPreview = {},
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.onNodeWithContentDescription("Previous channel").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Next channel").assertIsNotEnabled()
        assertEquals(1, toggles)
    }

    @Test fun `interaction restarts the full hide interval`() {
        val state = startAutoHide()
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { state.controlsInteractionNonce++ }
        applyStateChanges()
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { assertTrue(state.controlsVisible) }
        rule.mainClock.advanceTimeBy(1_200)
        rule.runOnIdle { assertFalse(state.controlsVisible) }
    }

    @Test fun `sheet holds controls and closing grants a new interval`() {
        val state = startAutoHide()
        rule.runOnIdle { state.showActionsSheet = true }
        applyStateChanges()
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle { assertTrue(state.controlsVisible); state.showActionsSheet = false }
        applyStateChanges()
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { assertTrue(state.controlsVisible) }
        rule.mainClock.advanceTimeBy(1_200)
        rule.runOnIdle { assertFalse(state.controlsVisible) }
    }

    @Test fun `press and keyboard focus each prevent hide`() {
        val state = startAutoHide()
        rule.runOnIdle { state.controlsPressed = true }
        applyStateChanges()
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle {
            assertTrue(state.controlsVisible)
            state.controlsPressed = false
            state.controlsFocused = true
        }
        applyStateChanges()
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle { assertTrue(state.controlsVisible) }
    }

    @Test fun `disabled artwork tone never resolves a logo`() {
        var resolutions = 0
        rule.setContent {
            rememberArtworkTone(M3uChannel("Test", "https://example.test/live"), {
                resolutions++
                null
            }, enabled = false)
        }
        rule.runOnIdle { assertEquals(0, resolutions) }
    }

    @Test fun `level controls remain operable at two hundred percent font size`() {
        val audio = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val initial = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, initial, 0)
        try {
            val state = PlayerScreenTransientState(null, audio)
            state.brightnessLevel = 0.5f
            rule.setContent {
                UaCastTheme(AppTheme.CINEMA) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                        PlayerLevelsSheet(state, PlayerScreenEnvironment(null, audio, LocalHapticFeedback.current))
                    }
                }
            }
            rule.onNodeWithContentDescription("Increase brightness, currently 50 percent")
                .performScrollTo().performClick()
            rule.onNodeWithContentDescription("Decrease volume", substring = true).performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(0.6f, state.brightnessLevel, 0.001f)
                assertEquals(initial - 1, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                assertEquals(audio.currentVolumeFraction(), state.volumeLevel, 0.001f)
            }
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
        }
    }

    private fun startAutoHide(): PlayerScreenTransientState {
        val state = PlayerScreenTransientState(null, null)
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerControlsAutoHide(state, true); Text("Video") }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertTrue("Controls start visible at ${rule.mainClock.currentTime}", state.controlsVisible) }
        return state
    }

    private fun applyStateChanges() {
        // With autoAdvance disabled, publish the UI-thread snapshot before advancing the
        // coroutine clock; otherwise the old effect's deadline runs before recomposition.
        rule.runOnIdle { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }
}
