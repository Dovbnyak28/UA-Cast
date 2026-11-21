package com.uacastplayer.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SleepTestApplication
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = SleepTestApplication::class, qualifiers = "en-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class FullscreenBrightnessSynchronizationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private val audio = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalBrightness = 0f
    private var originalVolume = 0

    @Before fun rememberLevels() {
        originalBrightness = rule.activity.window.attributes.screenBrightness
        originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    @After fun close() {
        store.clear()
        setBrightness(originalBrightness)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
    }

    @Test fun upwardSwipeCannotDimAfterBrightnessChangedOutsideThePlayer() {
        setBrightness(0.2f)
        val state = PlayerScreenTransientState(rule.activity, audio).apply { controlsVisible = false }
        show(state)
        rule.runOnIdle { setBrightness(0.8f) }

        swipeBrightness(up = true)

        rule.runOnIdle {
            assertEquals(GestureIndicatorKind.BRIGHTNESS, state.gestureIndicator)
            assertTrue("Upward swipe dimmed the actual window", rule.activity.window.attributes.screenBrightness > 0.8f)
            assertEquals(rule.activity.window.attributes.screenBrightness, state.brightnessLevel, 0.001f)
        }
    }

    @Test fun downwardSwipeCannotBrightenAfterBrightnessChangedOutsideThePlayer() {
        setBrightness(0.8f)
        val state = PlayerScreenTransientState(rule.activity, audio).apply { controlsVisible = false }
        show(state)
        rule.runOnIdle { setBrightness(0.2f) }

        swipeBrightness(up = false)

        rule.runOnIdle {
            assertEquals(GestureIndicatorKind.BRIGHTNESS, state.gestureIndicator)
            assertTrue("Downward swipe brightened the actual window",
                rule.activity.window.attributes.screenBrightness < 0.2f)
            assertEquals(rule.activity.window.attributes.screenBrightness, state.brightnessLevel, 0.001f)
        }
    }

    private fun swipeBrightness(up: Boolean) {
        rule.onNodeWithTag("fullscreen_gesture_surface").performTouchInput {
            val startY = height * if (up) 0.6f else 0.4f
            val endY = height * if (up) 0.4f else 0.6f
            swipe(Offset(width * 0.15f, startY), Offset(width * 0.15f, endY), durationMillis = 300)
        }
    }

    private fun show(state: PlayerScreenTransientState) {
        val app = ApplicationProvider.getApplicationContext<SleepTestApplication>()
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[PlayerViewModel::class.java]
        val sleepTimer = SleepTimerState(mutableStateOf<Long?>(null), {}, {})
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                FullscreenPlayerContent(
                    content = PlayerScreenContent(PlayerUiState(isBuffering = false), DlnaConnectionState(), Unit,
                        AspectRatioFrameLayout.RESIZE_MODE_FIT),
                    actions = PlayerScreenActions(vm, {}, { false }, {}, { null }, {}),
                    environment = PlayerScreenEnvironment(rule.activity, audio, LocalHapticFeedback.current),
                    transientState = state,
                    sleepTimer = sleepTimer,
                    modifier = Modifier.testTag("fullscreen_gesture_surface"),
                    videoSurface = { _, modifier -> Box(modifier) },
                )
            }
        }
    }

    private fun setBrightness(level: Float) {
        rule.activity.window.attributes = rule.activity.window.attributes.apply { screenBrightness = level }
    }
}
