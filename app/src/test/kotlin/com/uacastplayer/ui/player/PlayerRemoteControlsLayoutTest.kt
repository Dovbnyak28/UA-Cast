package com.uacastplayer.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.uacastplayer.R
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class PlayerRemoteControlsLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `local audio action disappears during remote playback and returns after disconnect`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val showAudio = mutableStateOf(true)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                QuickSettingsRow({}, {}, {}, showAudio = showAudio.value)
            }
        }
        val audio = rule.onNodeWithText(context.getString(R.string.player_audio_track))
        audio.assertIsDisplayed()
        rule.runOnIdle { showAudio.value = false }
        audio.assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.player_tv_guide)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.player_more_controls)).assertIsDisplayed()
        rule.runOnIdle { showAudio.value = true }
        audio.assertIsDisplayed()
    }
}
