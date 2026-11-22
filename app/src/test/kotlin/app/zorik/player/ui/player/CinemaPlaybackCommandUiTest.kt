package app.zorik.player.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import app.zorik.player.player.PlayerUiState
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w640dp-h360dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaPlaybackCommandUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun largeTextUsesAnAccessibleDeviceIconInsteadOfSplittingWords() {
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            ZorikTheme(AppTheme.CINEMA) { OtherTvButton(false, {}) }
        } }
        rule.onNodeWithText("Other TVs").assertDoesNotExist()
        rule.onNodeWithContentDescription("Other devices (DLNA)").assertExists()
    }

    @Test @Config(qualifiers = "en-w320dp-h640dp-xhdpi")
    fun activeTimerCannotSqueezeOutWindowControlsAtTwoHundredPercent() {
        val timer = mutableStateOf<Long?>(3_600_000L)
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
            ZorikTheme(AppTheme.CINEMA) {
                PlayerControlsOverlay(PlayerUiState(currentChannel = M3uChannel("Live", "https://example.test/live"),
                    canControlPlayback = true), true, timer,
                    {}, {}, {}, {}, {}, {}, {}, false, {}, {}, onOpenGuide = {})
            }
        } }
        rule.onNodeWithText("60:00").assertIsDisplayed()
        val fullscreen = rule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Timer must leave a full48dp window action", fullscreen.width / rule.density.density >= 47.9f)
    }

    @Test fun unsupportedPauseIsAbsentFromFullscreen() {
        val timer = mutableStateOf<Long?>(null)
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            PlayerControlsOverlay(
                PlayerUiState(currentChannel = M3uChannel("Live", "https://example.test/live"),
                    wantsToPlay = true, canControlPlayback = false), true, timer,
                {}, {}, {}, {}, {}, {}, {}, false, {}, {},
            )
        } }
        rule.onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    @Test fun unseekableVideoHasNoSliderOrRelativeSeek() {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            FilePlaybackProgress(FilePlaybackTimeline(5_000, 30_000), false, {})
        } }
        rule.onNodeWithContentDescription("Video position").assertDoesNotExist()
        rule.onNodeWithText("−10 sec").assertDoesNotExist()
        rule.onNodeWithText("+10 sec").assertDoesNotExist()
    }

    @Test fun unknownDurationHasNoSliderEvenIfCommandIsAvailable() {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            FilePlaybackProgress(FilePlaybackTimeline(5_000, 0), true, {})
        } }
        rule.onNodeWithContentDescription("Video position").assertDoesNotExist()
    }

    @Test fun directGuideDoesNotExecutePlayback() {
        val timer = mutableStateOf<Long?>(null)
        var guides = 0
        var toggles = 0
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            PlayerControlsOverlay(
                PlayerUiState(currentChannel = M3uChannel("Live", "https://example.test/live"),
                    wantsToPlay = true, canControlPlayback = true), true, timer,
                {}, { toggles++ }, {}, {}, {}, {}, {}, false, {}, {}, onOpenGuide = { guides++ },
            )
        } }
        rule.onNodeWithContentDescription("TV guide").performClick()
        rule.runOnIdle { assertEquals(1, guides); assertEquals(0, toggles) }
    }

    @Test fun seekableNetworkVodUsesTheExistingTimeline() {
        val timer = mutableStateOf<Long?>(null)
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            PlayerControlsOverlay(
                PlayerUiState(currentChannel = M3uChannel("VOD", "https://example.test/film.mp4"),
                    canControlPlayback = true, canSeek = true), true, timer,
                {}, {}, {}, {}, {}, {}, {}, false, {}, {}, fileProgress = { Text("Network timeline") },
            )
        } }
        rule.onNodeWithText("Network timeline").assertIsDisplayed()
        rule.onNodeWithText("LIVE").assertDoesNotExist()
    }
}
