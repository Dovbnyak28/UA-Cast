package app.zorik.player.ui.player

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlayerLevelSynchronizationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val audio = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalVolume = 0
    private var originalBrightness = 0f
    private var originalSystemBrightness: String? = null

    @Before fun rememberLevels() {
        originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        originalBrightness = rule.activity.window.attributes.screenBrightness
        originalSystemBrightness = Settings.System.getString(
            rule.activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
        )
    }

    @After fun restoreLevels() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        setBrightness(originalBrightness)
        Settings.System.putString(
            rule.activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, originalSystemBrightness,
        )
    }

    @Test fun openingLevelsReadsEffectiveBrightnessInsteadOfPlayerOpeningSnapshot() {
        setBrightness(0.2f)
        val state = PlayerScreenTransientState(rule.activity, audio)
        setBrightness(0.8f)

        show(state)

        rule.runOnIdle { assertEquals(0.8f, state.brightnessLevel, 0.001f) }
    }

    @Test fun increaseCannotDimAfterBrightnessChangedOutsideTheSheet() {
        setBrightness(0.2f)
        val state = PlayerScreenTransientState(rule.activity, audio)
        show(state)
        rule.runOnIdle { setBrightness(0.8f) }

        rule.onNodeWithContentDescription("Increase brightness", substring = true).performClick()

        rule.runOnIdle {
            assertEquals(0.9f, rule.activity.window.attributes.screenBrightness, 0.001f)
            assertEquals(0.9f, state.brightnessLevel, 0.001f)
        }
    }

    @Test fun decreaseCannotBrightenAfterBrightnessChangedOutsideTheSheet() {
        setBrightness(0.8f)
        val state = PlayerScreenTransientState(rule.activity, audio)
        show(state)
        rule.runOnIdle { setBrightness(0.2f) }

        rule.onNodeWithContentDescription("Decrease brightness", substring = true).performClick()

        rule.runOnIdle {
            assertEquals(0.1f, rule.activity.window.attributes.screenBrightness, 0.001f)
            assertEquals(0.1f, state.brightnessLevel, 0.001f)
        }
    }

    @Test fun hardwareVolumeChangesUpdateTheAlreadyOpenSheet() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0)
        val state = PlayerScreenTransientState(rule.activity, audio)
        show(state)
        rule.runOnIdle { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0) }

        rule.mainClock.advanceTimeBy(1_100)

        rule.runOnIdle { assertEquals(audio.currentVolumeFraction(), state.volumeLevel, 0.001f) }
    }

    @Test fun systemBrightnessChangesUpdateAnOpenSheetWhileTheWindowFollowsSystem() {
        setBrightness(-1f)
        Settings.System.putInt(rule.activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 64)
        val state = PlayerScreenTransientState(rule.activity, audio)
        show(state)
        rule.runOnIdle {
            Settings.System.putInt(rule.activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 204)
        }

        rule.mainClock.advanceTimeBy(1_100)

        rule.runOnIdle { assertEquals(204f / 255f, state.brightnessLevel, 0.001f) }
    }

    private fun show(state: PlayerScreenTransientState) {
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                PlayerLevelsSheet(state, PlayerScreenEnvironment(rule.activity, audio, LocalHapticFeedback.current))
            }
        }
    }

    private fun setBrightness(level: Float) {
        rule.activity.window.attributes = rule.activity.window.attributes.apply { screenBrightness = level }
    }
}
