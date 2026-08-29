package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.player.PlayerControlsOverlay
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
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
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    PlayerControlsOverlay(
                        uiState = PlayerUiState(
                            currentChannel = current,
                            isPlaying = true,
                            nextChannelsPreview = previews,
                        ),
                        isFullscreen = true,
                        sleepTimerRemainingMillis = remember { mutableStateOf<Long?>(null) },
                        brightnessLevel = 0.4f,
                        volumeLevel = 0.7f,
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
                        onBrightnessStep = {},
                        onVolumeStep = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/player_controls_landscape.png")
    }
}
