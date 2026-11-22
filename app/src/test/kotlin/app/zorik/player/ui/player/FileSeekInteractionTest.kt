package app.zorik.player.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class FileSeekInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val enabled = mutableStateOf(true)
    private val seeks = mutableListOf<Long>()
    private val slider get() = rule.onNodeWithContentDescription("Video position")

    @Test fun losingSeekPermissionDuringAGestureRestoresTheActualPosition() {
        render()
        slider.performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.8f, centerY), delayMillis = 200)
        }
        assertTrue(shownPosition() > 0.5f)
        rule.runOnIdle { enabled.value = false }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { up() }
        rule.runOnIdle { assertTrue(seeks.isEmpty()) }
        slider.assertDoesNotExist()
        rule.runOnIdle { enabled.value = true }
        rule.waitForIdle()
        assertEquals(0.1f, shownPosition(), 0.001f)
    }

    @Test fun completedDragSeeksOnce() {
        render()
        slider.performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.8f, centerY), delayMillis = 200)
            up()
        }
        rule.runOnIdle { assertEquals(1, seeks.size); assertTrue(seeks.single() > 50_000) }
    }

    private fun shownPosition() = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun render() {
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                FilePlaybackProgress(FilePlaybackTimeline(10_000, 100_000), enabled.value, { seeks += it })
            }
        }
    }
}
