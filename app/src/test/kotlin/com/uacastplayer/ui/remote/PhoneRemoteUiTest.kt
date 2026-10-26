package com.uacastplayer.ui.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.uacastplayer.R
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.remote.PhoneRemoteState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PhoneRemoteUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `chooser exposes both app-only navigation modes`() {
        val selected = mutableListOf<PhoneRemoteMode>()
        rule.setContent { UaCastTheme(AppTheme.CINEMA) { RemoteModeChooser({ selected.add(it) }, {}) } }
        rule.onNodeWithText(context.getString(R.string.remote_mode_buttons)).performClick()
        rule.onNodeWithText(context.getString(R.string.remote_mode_touchpad)).performClick()
        assertEquals(listOf(PhoneRemoteMode.REMOTE, PhoneRemoteMode.TOUCHPAD), selected)
    }

    @Test fun `pairing remains usable on a small phone and passes the exact endpoint and code`() {
        var connected: Pair<String, String>? = null
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PhoneRemoteDialog(PhoneRemoteMode.REMOTE, {}, PhoneRemoteState(),
                    { address, code -> connected = address to code }, { true }, {})
            }
        }
        rule.onNodeWithTag("remote_address").performScrollTo().performTextInput("192.168.1.20:4567")
        rule.onNodeWithTag("remote_code").performScrollTo().performTextInput("12345678")
        rule.onNodeWithText(context.getString(R.string.remote_connect)).performScrollTo().performClick()
        assertEquals("192.168.1.20:4567" to "12345678", connected)
        rule.onNodeWithText(context.getString(R.string.common_close)).performScrollTo().assertIsDisplayed()
    }

    @Test fun `D-pad emits one command per press and mode change replaces controls`() {
        val mode = mutableStateOf(PhoneRemoteMode.REMOTE)
        val commands = mutableListOf<RemoteCommand>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column { RemoteNavigationControls(mode.value) { commands.add(it); true } }
            }
        }
        rule.onNodeWithTag("remote_UP").performClick()
        rule.onNodeWithTag("remote_SELECT").performClick()
        assertEquals(listOf(RemoteCommand.UP, RemoteCommand.SELECT), commands)
        rule.runOnIdle { mode.value = PhoneRemoteMode.TOUCHPAD }
        rule.onNodeWithTag("remote_UP").assertDoesNotExist()
        rule.onNodeWithTag("remote_touchpad").assertIsDisplayed().performTouchInput { click() }
        assertEquals(RemoteCommand.SELECT, commands.last())
        rule.onNodeWithTag("remote_touchpad").performTouchInput { swipeRight() }
        assertTrue(commands.drop(3).isNotEmpty())
        assertTrue(commands.drop(3).all { it == RemoteCommand.RIGHT })
    }
}
