package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.player.PlaybackFailureCard
import com.uacastplayer.ui.playlist.AddPlaylistScreen
import com.uacastplayer.ui.settings.SettingsOverview
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class AuditSurfacesScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addPlaylist_emptyValidatedForm() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                AddPlaylistScreen(
                    playlistState = PlaylistUiState(),
                    onSetDisplayName = {},
                    onLoadUrl = {},
                    onPickFile = {},
                    onLoadXtream = { _, _, _ -> },
                    onBackClick = {},
                    onPlaylistLoaded = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/add_playlist_empty_cinema.png")
    }

    @Test
    fun settingsOverview_cinema() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column(
                    modifier = Modifier
                        .size(width = 411.dp, height = 700.dp)
                        .background(UaTheme.palette.void)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsOverview({}, {}, {}, {}, {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_overview_cinema.png")
    }

    @Test
    fun playerFatalRecovery_cinema() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(
                    modifier = Modifier
                        .size(width = 411.dp, height = 400.dp)
                        .background(androidx.compose.ui.graphics.Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaybackFailureCard(onRetry = {}, onNext = {}, onExit = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/player_fatal_recovery_cinema.png")
    }
}
