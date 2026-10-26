package com.uacastplayer.ui.player

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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.util.UnstableApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.R
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
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
@Config(qualifiers = "w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@UnstableApi
class FilePlayerCompactLayoutTest(private val locale: String) {
    @get:Rule val rule = createComposeRule()

    @Test fun anActiveSleepTimerDoesNotPushControlsOutsideACompactScreen() {
        val video = LocalVideo("content://media/video/1", "Сімейна подорож — Viaje familiar.mp4", "video/mp4")
        val state = PlayerUiState(currentChannel = M3uChannel(video.displayName, video.uri, localVideo = video),
            canControlPlayback = true)
        render(state, "file")
        assertControlsContained(listOf(R.string.player_play, R.string.player_sleep_timer,
            R.string.player_picture_in_picture, R.string.player_exit_fullscreen))
        rule.onNodeWithText("1:05").assertIsDisplayed()
        rule.onNodeWithText("1:01:01").assertIsDisplayed()
    }

    @Test fun liveChannelNavigationAlsoRemainsVisibleWithLargeTextAndAnActiveTimer() {
        render(PlayerUiState(currentChannel = M3uChannel("Новини України HD", "https://unused.invalid/live"),
            canControlPlayback = true, canGoPrevious = true, canGoNext = true), "live")
        assertControlsContained(listOf(R.string.player_play, R.string.player_previous, R.string.player_next,
            R.string.player_sleep_timer, R.string.player_picture_in_picture, R.string.player_exit_fullscreen))
    }

    private fun render(state: PlayerUiState, name: String) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h480dp-xhdpi")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                UaCastTheme(AppTheme.CINEMA) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        PlayerControlsOverlay(state, true, remember { mutableStateOf(3_661_000L) },
                            {}, {}, {}, {}, {}, {}, {},
                            false, {}, {}, {}, fileProgress = {
                                FilePlaybackProgress(FilePlaybackTimeline(65_000, 3_661_000), true, {},
                                    Modifier.padding(horizontal = 20.dp))
                            })
                    }
                }
            }
        }
        // Only UK has a reviewed, tracked golden. build/ diagnostics are not baselines on a
        // fresh CI checkout. All locales still run the same strict control geometry assertions.
        if (locale == "uk") {
            rule.onRoot().captureRoboImage("src/test/screenshots/${name}_player_compact_uk.png")
        }
    }

    private fun assertControlsContained(labels: List<Int>) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val viewport = rule.onRoot().getUnclippedBoundsInRoot()
        labels.forEach { label ->
            val control = rule.onNodeWithContentDescription(app.getString(label)).assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assertTrue("$locale ${app.getString(label)} outside $viewport: $control",
                control.left >= viewport.left && control.right <= viewport.right && control.bottom <= viewport.bottom &&
                    control.right - control.left >= 38.dp && control.bottom - control.top >= 38.dp)
        }
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "locale={0}")
        fun locales() = listOf(arrayOf("en"), arrayOf("uk"), arrayOf("ru"), arrayOf("es"))
    }
}
