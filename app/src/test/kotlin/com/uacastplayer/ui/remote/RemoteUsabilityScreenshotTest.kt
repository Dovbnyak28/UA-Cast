package com.uacastplayer.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.remote.PhoneRemoteState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.appBackground
import com.uacastplayer.ui.tv.TvBrowseScreen
import com.uacastplayer.ui.tv.TvInputRegistry
import com.uacastplayer.ui.tv.TvPresentation
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class RemoteUsabilityScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @After fun restoreFontScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test @Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
    fun modeChoicesAtTwoHundredPercent() {
        RuntimeEnvironment.setFontScale(2f)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Column(Modifier.fillMaxSize().appBackground().padding(40.dp)) {
                        RemoteModeSelector(PhoneRemoteMode.REMOTE, {})
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/phone_remote_modes_large.png")
    }

    @Test fun actualConnectedDpadDialog() = captureDialog(PhoneRemoteMode.REMOTE, "phone_remote_dialog_dpad")

    @Test fun actualConnectedTouchpadDialog() = captureDialog(PhoneRemoteMode.TOUCHPAD, "phone_remote_dialog_touchpad")

    @Test @Config(qualifiers = "uk-w960dp-h540dp-television-xhdpi")
    fun emptyTvFavoritesExplainTheNextStep() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    Box(Modifier.fillMaxSize().appBackground()) {
                        TvBrowseScreen(BottomDestination.FAVORITES, PlaylistUiState(),
                            HomeSourceState(emptyList(), null, {}, {}, {}, {}), emptyList(), emptySet(),
                            { null }, { _, _ -> })
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/tv_favorites_empty.png")
    }

    @Test @Config(qualifiers = "uk-w960dp-h540dp-television-xhdpi")
    fun noTvSearchMatchesOfferReachableClear() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    Box(Modifier.fillMaxSize().appBackground()) {
                        TvBrowseScreen(BottomDestination.CHANNELS,
                            PlaylistUiState(channels = listOf(M3uChannel("Новини", "https://example.test/live"))),
                            HomeSourceState(emptyList(), null, {}, {}, {}, {}), emptyList(), emptySet(),
                            { null }, { _, _ -> })
                    }
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_search").performTextInput("Немає такого каналу")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("tv_search").performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("tv_search_clear_empty").assertIsFocused()
        rule.onRoot().captureRoboImage("src/test/screenshots/tv_search_no_results.png")
    }

    private fun captureDialog(mode: PhoneRemoteMode, name: String) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.fillMaxSize().appBackground().padding(16.dp)) {
                    PhoneRemoteDialog(mode, {}, PhoneRemoteState(connected = true), { _, _ -> }, { true }, {})
                }
            }
        }
        rule.onNodeWithTag("phone_remote_dialog").captureRoboImage("src/test/screenshots/$name.png")
    }
}
