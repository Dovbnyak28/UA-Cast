@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package app.zorik.player.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.R
import app.zorik.player.media.LocalVideo
import app.zorik.player.player.IndexedChannel
import app.zorik.player.player.PlayerUiState
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.LocalReducedEffects
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h240dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class PlayerShortWindowTest(private val locale: String) {
    @get:Rule val rule = createComposeRule()
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var toggles = 0
    private var previewIndex: Int? = null

    @Test fun liveTransportFitsShortLandscapeAtTwoHundredPercent() {
        render(liveState(), "live")
        assertContained(R.string.common_back, R.string.player_play, R.string.player_previous,
            R.string.player_next, R.string.player_exit_fullscreen, R.string.player_sleep_timer)
        rule.onNodeWithContentDescription(context.getString(R.string.player_play)).performClick()
        assertEquals(1, toggles)
    }

    @Test fun fileSeekingAndTransportFitShortLandscapeAtTwoHundredPercent() {
        val video = LocalVideo("content://media/video/1", "Сімейна подорож — Viaje familiar.mp4", "video/mp4")
        render(PlayerUiState(currentChannel = M3uChannel(video.displayName, video.uri, localVideo = video),
            canControlPlayback = true), "file")
        assertContained(R.string.common_back, R.string.player_play, R.string.player_exit_fullscreen,
            R.string.player_sleep_timer, R.string.local_video_seek)
        rule.onNodeWithText("1:05").assertIsDisplayed()
        rule.onNodeWithText("1:01:01").assertIsDisplayed()
    }

    @Test fun shortChannelNamesHaveSeparateTouchTargets() {
        // The chip row belongs to the full panel; phone landscape heights get the compact one.
        render(liveState(), "chips", height = 520)
        val first = rule.onNodeWithText("1").assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
        val second = rule.onNodeWithText("2").assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
        assertTrue("Adjacent channel touch targets overlap: $first and $second", first.right <= second.left)
        rule.onNodeWithText("2").performClick()
        assertEquals(2, previewIndex)
    }

    @Test fun phoneLandscapeUsesCompactControlsSoTheVideoStaysVisible() {
        // A Mi A2 in landscape is 392dp tall; the full panel with its chip row covered about half
        // the video there. The compact panel drops the chips and keeps the transport.
        render(liveState(), "phone_landscape", height = 392)
        rule.onNodeWithText("1").assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.player_next)).assertIsDisplayed()
    }

    private fun liveState() = PlayerUiState(
        currentChannel = M3uChannel("Новини України HD", "https://unused.invalid/live"),
        canControlPlayback = true, canGoPrevious = true, canGoNext = true,
        nextChannelsPreview = (1..3).map { IndexedChannel(it, M3uChannel("$it", "https://unused.invalid/$it")) },
    )

    private fun render(state: PlayerUiState, name: String, height: Int = 240) {
        RuntimeEnvironment.setQualifiers("$locale-w640dp-h${height}dp-land-xhdpi")
        RuntimeEnvironment.setFontScale(2f)
        rule.setContent {
            CompositionLocalProvider(LocalReducedEffects provides true,
                LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                ZorikTheme(AppTheme.CINEMA) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        PlayerControlsOverlay(state, true, remember { mutableStateOf(3_661_000L) },
                            {}, { toggles++ }, {}, {}, {}, {}, {}, false, {}, { previewIndex = it.index },
                            fileProgress = {
                                FilePlaybackProgress(FilePlaybackTimeline(65_000, 3_661_000), true, {},
                                    Modifier.padding(horizontal = 20.dp))
                            })
                    }
                }
            }
        }
        if (locale == "uk") rule.onRoot().captureRoboImage("src/test/screenshots/player_short_${name}_uk.png")
    }

    private fun assertContained(vararg labels: Int) {
        val viewport = rule.onRoot().getUnclippedBoundsInRoot()
        labels.forEach { label ->
            val name = context.getString(label)
            val bounds = rule.onNodeWithContentDescription(name).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$locale $name outside $viewport: $bounds",
                bounds.top >= viewport.top && bounds.bottom <= viewport.bottom &&
                    bounds.left >= viewport.left && bounds.right <= viewport.right)
        }
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "locale={0}")
        fun locales() = listOf(arrayOf("en"), arrayOf("uk"), arrayOf("ru"), arrayOf("es"))
    }
}
