package com.uacastplayer.ui.remote

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.R
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.data.remote.PhoneRemoteClient
import com.uacastplayer.data.remote.TvRemoteServer
import com.uacastplayer.remote.PhoneRemoteState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated Compose content and loopback only: never edits user playlists or contacts a real TV. */
@RunWith(AndroidJUnit4::class)
class RemoteControlInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun chooserOffersBothAppOnlyModes() {
        val selected = mutableListOf<PhoneRemoteMode>()
        rule.setContent { UaCastTheme(AppTheme.CINEMA) { RemoteModeChooser({ selected.add(it) }, {}) } }
        rule.onNodeWithText(context.getString(R.string.remote_mode_buttons)).assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.remote_mode_touchpad)).assertIsDisplayed().performClick()
        assertEquals(listOf(PhoneRemoteMode.REMOTE, PhoneRemoteMode.TOUCHPAD), selected)
        screenshot("chooser")
    }

    @Test fun pairingFormAndCloseRemainReachableWithTheKeyboard() {
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
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText(context.getString(R.string.common_close)).performScrollTo().assertIsDisplayed()
        screenshot("pairing")
    }

    @Test fun nativeTouchRuntimeDispatchesDpadTapAndSwipeWithoutDuplicateSelection() {
        val mode = mutableStateOf(PhoneRemoteMode.REMOTE)
        val commands = mutableListOf<RemoteCommand>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PhoneRemoteDialog(mode.value, { mode.value = it }, PhoneRemoteState(connected = true),
                    { _, _ -> }, { commands.add(it); true }, {})
            }
        }
        rule.onNodeWithTag("remote_UP").performScrollTo().performClick()
        rule.onNodeWithTag("remote_SELECT").performScrollTo().performClick()
        assertEquals(listOf(RemoteCommand.UP, RemoteCommand.SELECT), commands)
        rule.onNodeWithText(context.getString(R.string.remote_previous_channel)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.remote_next_channel)).performScrollTo().assertIsDisplayed()
        screenshot("dpad")
        rule.onNodeWithText(context.getString(R.string.remote_mode_touchpad)).performScrollTo().performClick()
        rule.onNodeWithTag("remote_UP").assertDoesNotExist()
        rule.onNodeWithTag("remote_touchpad").performScrollTo().assertIsDisplayed().performTouchInput { click() }
        assertEquals(listOf(RemoteCommand.UP, RemoteCommand.SELECT, RemoteCommand.SELECT), commands)
        rule.onNodeWithTag("remote_touchpad").performTouchInput { swipeRight() }
        assertTrue(commands.drop(3).isNotEmpty())
        assertTrue(commands.drop(3).all { it == RemoteCommand.RIGHT })
        screenshot("touchpad")
    }

    @Test fun realAndroidCryptoAndSocketsAuthenticateAndAcknowledgeEveryCommand() {
        val received = ConcurrentLinkedQueue<RemoteCommand>()
        TvRemoteServer("127.0.0.1", { _, command -> received.add(command) }, { _, _ -> }).use { server ->
            PhoneRemoteClient().use { client ->
                client.connect(server.endpoint, server.pairingCode)
                RemoteCommand.entries.forEach(client::send)
            }
        }
        assertEquals(RemoteCommand.entries.toList(), received.toList())
    }

    private fun screenshot(label: String) {
        rule.waitForIdle()
        // Compose semantics can be current before SurfaceFlinger presents the new dialog frame.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: checkNotNull(context.getExternalFilesDir(null))
        directory.mkdirs()
        val file = File(directory, "remote-$label.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
