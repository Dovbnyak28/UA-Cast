@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.player.PlaybackActivity
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SleepTestApplication
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.tv.TvInputRegistry
import com.uacastplayer.ui.tv.TvPresentation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
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
@Config(application = SleepTestApplication::class, qualifiers = "en-w960dp-h540dp-television-xhdpi")
@Category(RequiresComposeTestManifest::class)
class TvPlayerCinemaInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<SleepTestApplication>()
    private val store = ViewModelStore()
    private val mounted = mutableStateOf(true)

    @After fun close() {
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { mounted.value = false }
        rule.waitForIdle()
        store.clear()
        PlaybackActivity.setActive(false)
    }

    @Test fun hiddenOkOnlyRevealsThenFocusedPauseExecutesOnce() {
        val vm = mount(actualPlaying = true)
        var intentChanges = 0
        rule.runOnIdle {
            vm.player.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    intentChanges++
                }
            })
        }
        rule.mainClock.advanceTimeBy(8_200)
        rule.onNodeWithText(CHANNEL_NAME).assertDoesNotExist()

        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        publishStateChanges()
        rule.onNodeWithText(CHANNEL_NAME).assertExists()
        rule.runOnIdle {
            assertTrue("The first OK reveals controls without pausing", vm.player.playWhenReady)
            assertEquals(0, intentChanges)
        }

        val pause = rule.onNode(hasText("Pause") or hasContentDescription("Pause"))
        pause.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pause.performKeyInput { pressKey(Key.DirectionCenter) }
        publishStateChanges()
        rule.runOnIdle {
            assertFalse(vm.player.playWhenReady)
            assertEquals("One complete key press dispatches only once", 1, intentChanges)
        }
    }

    @Test fun actualPlayingKeepsControlsForEightSeconds() {
        mount(actualPlaying = true)
        rule.mainClock.advanceTimeBy(7_900)
        rule.onNodeWithText(CHANNEL_NAME).assertExists()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText(CHANNEL_NAME).assertDoesNotExist()
    }

    @Test fun bufferingWithPlayIntentDoesNotHideControls() {
        mount(actualPlaying = false)
        rule.mainClock.advanceTimeBy(20_000)
        rule.onNodeWithText(CHANNEL_NAME).assertExists()
    }

    @Test fun openDialogHoldsControlsAndClosingStartsNewEightSecondInterval() {
        mount(actualPlaying = true)
        // Direct lazy scroll has no animation dependency on the deliberately paused clock.
        rule.onAllNodes(hasScrollAction())[0].performScrollToIndex(7)
        rule.onNodeWithText("Sleep timer").performClick()
        publishStateChanges()
        rule.mainClock.advanceTimeBy(20_000)
        rule.onNodeWithText(CHANNEL_NAME).assertExists()
        rule.onNodeWithText("Close").performClick()
        publishStateChanges()
        rule.mainClock.advanceTimeBy(7_900)
        rule.onNodeWithText(CHANNEL_NAME).assertExists()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText(CHANNEL_NAME).assertDoesNotExist()
    }

    private fun mount(actualPlaying: Boolean): PlayerViewModel {
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[PlayerViewModel::class.java]
        // Reuse the real VM/engine and capability projection, but never prepare a network source.
        vm.player.pause()
        vm.setRemoteCastingForLifecycleTest(chromecast = true, dlna = false)
        vm.start(listOf(M3uChannel(CHANNEL_NAME, "http://127.0.0.1:9/cinema-tv")), 0)
        vm.setRemoteCastingForLifecycleTest(chromecast = false, dlna = false)
        vm.player.playWhenReady = true
        // UI-only playback events isolate the timer from unavailable emulator/Robolectric decoders.
        uiEvents(vm).value = vm.uiState.value.copy(isPlaying = actualPlaying, isBuffering = !actualPlaying)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    if (mounted.value) TvPlayerScreen(vm, {}, { false }, {}, EpgUiState())
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle {
            // Attaching PlayerView can publish an initial engine event. Apply the synthetic
            // observed state after attachment so it cannot race that initial callback.
            uiEvents(vm).value = vm.uiState.value.copy(isPlaying = actualPlaying, isBuffering = !actualPlaying)
        }
        publishStateChanges()
        return vm
    }

    @Suppress("UNCHECKED_CAST") // Test-only event injection; production ownership remains private.
    private fun uiEvents(vm: PlayerViewModel): MutableStateFlow<PlayerUiState> =
        PlayerViewModel::class.java.getDeclaredField("_uiState").let {
            it.isAccessible = true
            it.get(vm) as MutableStateFlow<PlayerUiState>
        }

    private fun publishStateChanges() {
        rule.runOnIdle { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    private companion object {
        const val CHANNEL_NAME = "Cinema TV regression"
    }
}
