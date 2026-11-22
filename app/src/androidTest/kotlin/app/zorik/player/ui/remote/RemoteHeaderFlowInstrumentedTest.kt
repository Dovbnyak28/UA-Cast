package app.zorik.player.ui.remote

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.R
import app.zorik.player.data.remote.TvRemoteServer
import app.zorik.player.remote.PhoneRemoteViewModel
import app.zorik.player.remote.TvRemoteReceiverViewModel
import app.zorik.player.ui.components.ScreenHeader
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class RemoteHeaderFlowInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private var server: TvRemoteServer? = null

    @After fun cleanup() { store.clear(); server?.close() }

    @Test fun minimizeKeepsTheAuthenticatedConnectionAndReopenDoesNotPairAgain() {
        val receiver = TvRemoteServer("127.0.0.1", { _, _ -> true }, { _, _ -> })
        server = receiver
        val phone = PhoneRemoteViewModel()
        val tv = TvRemoteReceiverViewModel(app)
        store.put("phone", phone); store.put("tv", tv)
        rule.setContent { ZorikTheme(AppTheme.DAYLIGHT) {
            RemoteControlHost(false, phone, tv) { ScreenHeader("Home", labelRemote = true) }
        } }
        rule.onNodeWithText(app.getString(R.string.remote_short_title)).assertIsDisplayed()
        rule.onNodeWithContentDescription(app.getString(R.string.remote_title)).performClick()
        rule.onNode(hasText(app.getString(R.string.remote_mode_buttons)) and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithTag("remote_address").performTextInput(receiver.endpoint.toString())
        rule.onNodeWithTag("remote_code").performTextInput(receiver.pairingCode)
        rule.onNodeWithText(app.getString(R.string.remote_connect)).performScrollTo().performClick()
        rule.waitUntil(15_000) {
            phone.state.value.connected || phone.state.value.failed
        }
        assertTrue("Pairing state: ${phone.state.value}", phone.state.value.connected)
        rule.onNodeWithText(app.getString(R.string.remote_minimize)).performScrollTo().performClick()
        rule.onNodeWithTag("phone_remote_dialog").assertDoesNotExist()
        assertTrue(phone.state.value.connected)
        rule.onNodeWithText("127.0.0.1").assertIsDisplayed()
        rule.onNodeWithContentDescription(app.getString(R.string.remote_title)).performClick()
        rule.onNodeWithTag("phone_remote_dialog").assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.remote_disconnect)).performScrollTo().performClick()
        assertFalse(phone.state.value.connected)
    }
}
