package com.uacastplayer.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.uacastplayer.player.AutoSkipRecoveryState
import com.uacastplayer.player.IndexedChannel
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlayerRecoveryUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fatalCard_exposesRetryNextAndExitActions() {
        var retried = false
        var next = false
        var exited = false
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaybackFailureCard(
                    onRetry = { retried = true },
                    onNext = { next = true },
                    onExit = { exited = true },
                )
            }
        }

        composeRule.onNodeWithText("Повторити цей канал").performClick()
        composeRule.onNodeWithText("Наступний канал").performClick()
        composeRule.onNodeWithText("Назад до каналів").performClick()

        assertTrue(retried)
        assertTrue(next)
        assertTrue(exited)
    }

    @Test
    fun autoSkipIndicator_explainsProgressAndCanBeCancelled() {
        var cancelled = false
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                AutoSkipRecoveryIndicator(
                    state = AutoSkipRecoveryState(skippedChannels = 2, totalChannels = 5),
                    onCancel = { cancelled = true },
                )
            }
        }

        composeRule.onNodeWithText("Канал недоступний — пробуємо наступний (2/5)").assertIsDisplayed()
        composeRule.onNodeWithText("Скасувати").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun fullscreenControls_explainLevelsAndExposePreviewAsARealAction() {
        var selected = false
        val preview = IndexedChannel(
            index = 1,
            channel = M3uChannel("Наступний тестовий канал", "https://example.test/next.m3u8"),
        )
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlayerControlsOverlay(
                    uiState = PlayerUiState(
                        currentChannel = M3uChannel("Поточний канал", "https://example.test/live.m3u8"),
                        nextChannelsPreview = listOf(preview),
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
                    onSelectPreview = { selected = true },
                    onBrightnessStep = {},
                    onVolumeStep = {},
                )
            }
        }

        composeRule.onNodeWithText("40%").assertIsDisplayed()
        composeRule.onNodeWithText("70%").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Зменшити яскравість, зараз 40 відсотків")
            .assertHasClickAction()
        composeRule.onNodeWithText("Наступний тестовий канал").assertHasClickAction().performClick()
        assertTrue(selected)
    }
}
