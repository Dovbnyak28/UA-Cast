package app.zorik.player.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.player.IndexedChannel
import app.zorik.player.player.PlayerUiState
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.player.PlayerControlsOverlay
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w891dp-h411dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlayerControlsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fullscreenControls_keepEveryControlInContext() {
        val current = M3uChannel("Новини України HD", "https://example.test/live.m3u8")
        val previews = listOf(
            "Кіно Прем’єра",
            "Дитячий світ",
            "Дуже довга назва наступного спортивного каналу",
        ).mapIndexed { index, name ->
            IndexedChannel(index + 1, M3uChannel(name, "https://example.test/$index.m3u8"))
        }

        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    PlayerControlsOverlay(
                        uiState = PlayerUiState(
                            currentChannel = current,
                            isPlaying = true,
                            wantsToPlay = true,
                            canControlPlayback = true,
                            canGoNext = true,
                            canGoPrevious = true,
                            nextChannelsPreview = previews,
                        ),
                        isFullscreen = true,
                        sleepTimerRemainingMillis = remember { mutableStateOf<Long?>(null) },
                        onExit = {},
                        onPlayPause = {},
                        onNext = {},
                        onPrevious = {},
                        onToggleFullscreen = {},
                        onEnterPip = {},
                        onOpenSleepTimer = {},
                        isDlnaCasting = false,
                        onOpenDlnaSheet = {},
                        onSelectPreview = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/player_controls_landscape.png")
    }
}
