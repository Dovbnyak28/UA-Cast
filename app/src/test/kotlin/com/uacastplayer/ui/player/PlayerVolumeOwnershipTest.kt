package com.uacastplayer.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlayerVolumeOwnershipTest {
    @get:Rule val rule = createComposeRule()
    private val audio = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    @After fun restore() { audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0) }

    @Test fun `decrease cannot raise volume after a hardware volume change`() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0)
        val state = PlayerScreenTransientState(null, audio)
        show(state)
        rule.runOnIdle { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0) }
        rule.onNodeWithContentDescription("Decrease volume", substring = true).performClick()
        rule.runOnIdle {
            assertTrue("Decrease restored stale loud volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC) < 3)
            assertEquals(audio.currentVolumeFraction(), state.volumeLevel, 0.001f)
        }
    }

    @Test fun `opening levels reads the current volume not the player opening snapshot`() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 10, 0)
        val state = PlayerScreenTransientState(null, audio)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0)
        show(state)
        rule.runOnIdle { assertEquals(audio.currentVolumeFraction(), state.volumeLevel, 0.001f) }
    }

    @Test fun `buttons use one actual volume step and remain bounded at both limits`() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        for (current in listOf(0, 1, max - 1, max)) {
            for (increase in listOf(false, true)) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, current, 0)
                val displayed = stepStreamVolume(audio, increase)
                val expected = (current + if (increase) 1 else -1).coerceIn(0, max)
                assertEquals(expected, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                assertEquals(audio.currentVolumeFraction(), displayed, 0.001f)
            }
        }
    }

    private fun show(state: PlayerScreenTransientState) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlayerLevelsSheet(state, PlayerScreenEnvironment(null, audio, LocalHapticFeedback.current))
            }
        }
    }
}
