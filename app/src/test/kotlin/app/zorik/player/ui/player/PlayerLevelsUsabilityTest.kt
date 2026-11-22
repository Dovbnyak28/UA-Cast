package app.zorik.player.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.R
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlayerLevelsUsabilityTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    @After fun restoreEnvironment() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        RuntimeEnvironment.setFontScale(1f)
    }

    @Test fun labelledWideSlidersCanBeGrabbedAndDragged() {
        val state = show(1f)
        rule.onNodeWithText(context.getString(R.string.player_brightness)).assertIsDisplayed()
        val slider = rule.onNodeWithTag("player_brightness_slider").performScrollTo()
        val bounds = slider.getUnclippedBoundsInRoot()
        slider.assertTouchHeightIsEqualTo(48.dp)
        assertTrue("Usable track width: $bounds", bounds.right - bounds.left >= 100.dp)
        slider.performTouchInput { swipe(center, centerRight) }
        rule.runOnIdle {
            assertTrue("Brightness after dragging: ${state.brightnessLevel}", state.brightnessLevel > 0.85f)
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/player_levels_modern.png")
    }

    @Test @Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
    fun volumeRemainsReachableAtTwoHundredPercentFont() {
        show(2f)
        rule.onNodeWithTag("player_volume_slider").performScrollTo().assertIsDisplayed()
        val label = rule.onNodeWithText(context.getString(R.string.player_volume)).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("The dialog must actually render at 200%", 2f, layout.layoutInput.density.fontScale, 0.01f)
        assertFalse("Volume label must not be clipped", layout.hasVisualOverflow)
        rule.onRoot().captureRoboImage("src/test/screenshots/player_levels_large_text.png")
    }

    private fun show(fontScale: Float): PlayerScreenTransientState {
        // ModalBottomSheet owns an Android dialog; a parent LocalDensity override alone is insufficient.
        RuntimeEnvironment.setFontScale(fontScale)
        val state = PlayerScreenTransientState(null, audio)
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                val stableState = remember { state }
                PlayerLevelsSheet(stableState, PlayerScreenEnvironment(null, audio, LocalHapticFeedback.current))
            }
        } }
        return state
    }
}
