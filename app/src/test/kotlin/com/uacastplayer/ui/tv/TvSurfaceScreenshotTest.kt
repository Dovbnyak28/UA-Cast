package com.uacastplayer.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.appBackground
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w960dp-h540dp-television-xhdpi")
@Category(RequiresComposeTestManifest::class)
class TvSurfaceScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @Test fun tvChannels() {
        val channels = List(40) { M3uChannel("Канал ${it + 1}", "https://example.test/$it.ts") }
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    Box(Modifier.fillMaxSize().appBackground()) {
                        TvBrowseScreen(BottomDestination.CHANNELS, PlaylistUiState(channels = channels),
                            HomeSourceState(emptyList(), null, {}, {}, {}, {}), emptyList(), emptySet(),
                            { null }, { _, _ -> })
                    }
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("src/test/screenshots/tv_channels.png")
    }
}
