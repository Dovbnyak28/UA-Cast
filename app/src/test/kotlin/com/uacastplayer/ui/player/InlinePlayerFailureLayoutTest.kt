package com.uacastplayer.ui.player

import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.R
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SleepTestApplication
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = SleepTestApplication::class, qualifiers = "en-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlinePlayerFailureLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    @After fun close() { store.clear() }

    @Test fun `fatal state puts recovery actions in viewport without duplicate content or inert audio`() {
        val application = ApplicationProvider.getApplicationContext<SleepTestApplication>()
        val vm = ViewModelProvider(
            store, ViewModelProvider.AndroidViewModelFactory(application),
        )[PlayerViewModel::class.java]
        val state = PlayerUiState(
            currentChannel = M3uChannel("Test channel", "https://unused.invalid/live"),
            fatalError = true, canGoNext = true,
        )
        var exits = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                InlinePlayerContent(
                    PlayerScreenContent(state, DlnaConnectionState(), 0, AspectRatioFrameLayout.RESIZE_MODE_FIT),
                    PlayerScreenActions(vm, { exits++ }, { false }, {}, { null }, {}),
                    PlayerScreenTransientState(null, null),
                )
            }
        }
        rule.onAllNodesWithText("Test channel").assertCountEquals(1)
        rule.onAllNodesWithText("Back to channels").assertCountEquals(1)
        rule.onNodeWithText("Retry this channel").assertIsDisplayed()
        rule.onNodeWithText("Next channel").assertIsDisplayed()
        rule.onNodeWithText(application.getString(R.string.player_audio_track)).assertDoesNotExist()
        rule.onRoot().captureRoboImage("src/test/screenshots/inline_player_fatal_cinema.png")
        rule.onNodeWithText("Back to channels").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, exits) }
    }
}
