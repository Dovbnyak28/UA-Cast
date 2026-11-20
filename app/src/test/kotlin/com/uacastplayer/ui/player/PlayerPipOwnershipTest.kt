package com.uacastplayer.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w785dp-h393dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlayerPipOwnershipTest {
    @get:Rule val rule = createComposeRule()
    private val host = Robolectric.buildActivity(PipRecordingActivity::class.java).setup()
    @After fun destroyHost() { host.pause().stop().destroy() }

    @Test fun `removing expanded player revokes auto PiP on the surviving Activity`() {
        val mounted = mutableStateOf(true)
        val playing = mutableStateOf(true)
        val activity = host.get()
        rule.setContent {
            if (mounted.value) {
                PlayerScreenEffects(
                    PlayerScreenEnvironment(activity, null, LocalHapticFeedback.current),
                    isFullscreen = true,
                    uiState = PlayerUiState(isPlaying = playing.value),
                    transientState = remember { PlayerScreenTransientState(null, null) },
                )
            }
        }
        rule.runOnIdle { assertTrue(activity.autoEnterEnabled) }
        rule.runOnIdle { playing.value = false }
        rule.runOnIdle { assertFalse(activity.autoEnterEnabled) }
        rule.runOnIdle { playing.value = true }
        rule.runOnIdle { assertTrue(activity.autoEnterEnabled) }
        rule.runOnIdle { mounted.value = false }
        rule.runOnIdle { assertFalse(activity.autoEnterEnabled) }
        rule.runOnIdle { mounted.value = true }
        rule.runOnIdle { assertTrue(activity.autoEnterEnabled) }
    }
    @Test fun fullscreenTransitionsApplyOnlyTheirTargetAndRestoreOnRemoval() {
        val mounted = mutableStateOf(true)
        val fullscreen = mutableStateOf(false)
        val playing = mutableStateOf(false)
        val activity = host.get()
        rule.setContent {
            if (mounted.value) {
                PlayerScreenEffects(
                    PlayerScreenEnvironment(activity, null, LocalHapticFeedback.current),
                    isFullscreen = fullscreen.value,
                    uiState = PlayerUiState(isPlaying = playing.value),
                    transientState = remember { PlayerScreenTransientState(null, null) },
                )
            }
        }
        rule.runOnIdle { activity.orientationRequests.clear(); fullscreen.value = true }
        rule.runOnIdle {
            assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE), activity.orientationRequests)
            activity.orientationRequests.clear()
            playing.value = true
        }
        rule.runOnIdle { assertTrue(activity.orientationRequests.isEmpty()); fullscreen.value = false }
        rule.runOnIdle {
            assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED), activity.orientationRequests)
            activity.orientationRequests.clear()
            fullscreen.value = true
        }
        rule.runOnIdle {
            assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE), activity.orientationRequests)
            activity.orientationRequests.clear()
            mounted.value = false
        }
        rule.runOnIdle {
            assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED), activity.orientationRequests)
        }
    }

}

/** Records the boundary calls; the test does not pretend Robolectric enters a system PiP window. */
class PipRecordingActivity : Activity() {
    val orientationRequests = mutableListOf<Int>()
    override fun setRequestedOrientation(value: Int) {
        orientationRequests += value
        super.setRequestedOrientation(value)
    }

    var autoEnterEnabled = false
        private set

    override fun setPictureInPictureParams(params: PictureInPictureParams) {
        autoEnterEnabled = params.isAutoEnterEnabled
    }
}
