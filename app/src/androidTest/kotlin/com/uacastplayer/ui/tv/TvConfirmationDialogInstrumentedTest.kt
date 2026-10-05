package com.uacastplayer.ui.tv

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.R
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.Feature
import com.uacastplayer.remote.dispatchTvRemote
import com.uacastplayer.ui.components.SetPinDialog
import com.uacastplayer.ui.components.ParentalControlPinDialog
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.diagnostics.DiagnosticsPreviewDialog
import com.uacastplayer.ui.premium.UnlockDialog
import com.uacastplayer.ui.player.TrackPickerDialog
import com.uacastplayer.ui.settings.BackupExportWarningDialog
import com.uacastplayer.ui.settings.ParentalControlSection
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android dialog windows, but isolated Compose content: never resets PINs or sends reports. */
@RunWith(AndroidJUnit4::class)
class TvConfirmationDialogInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val showing = mutableStateOf(true)
    private lateinit var registry: TvInputRegistry
    private var dismissed = 0
    private var actions = 0
    private var underlyingBack = 0
    private var underlyingSelect = 0

    @Before fun debugOnly() {
        assertEquals("com.uacastplayer.debug", rule.activity.packageName)
    }

    @Test fun pinDialogOwnsBackAndSelect() {
        showDialog { dismiss, action -> SetPinDialog({ action() }, dismiss) }
        checkDismissal(R.string.common_cancel)
    }

    @Test fun pinCreationFieldsCanBeLeftUsingPhoneDpad() {
        showDialog { dismiss, action -> SetPinDialog({ action() }, dismiss) }
        rule.onNodeWithText(rule.activity.getString(R.string.parental_control_new_pin))
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        send(RemoteCommand.DOWN)
        rule.onNodeWithText(rule.activity.getString(R.string.parental_control_confirm_pin)).assertIsFocused()
        send(RemoteCommand.DOWN)
        rule.onNodeWithText(rule.activity.getString(R.string.common_cancel)).assertIsFocused()
        send(RemoteCommand.SELECT)
        assertEquals(1, dismissed)
        assertEquals(0, actions)
        assertReleased()
    }

    @Test fun pinVerificationFieldCanBeLeftUsingPhoneDpad() {
        showDialog { dismiss, action -> ParentalControlPinDialog("Fixture PIN", false, { action() }, dismiss) }
        rule.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        send(RemoteCommand.DOWN)
        rule.onNodeWithText(rule.activity.getString(R.string.common_cancel)).assertIsFocused()
        send(RemoteCommand.SELECT)
        assertEquals(1, dismissed)
        assertEquals(0, actions)
        assertReleased()
    }

    @Test fun premiumDialogOwnsBackAndSelect() {
        showDialog { dismiss, action -> UnlockDialog(Feature.BACKUP, action, dismiss) }
        checkDismissal(R.string.premium_unlock_later)
    }

    @Test fun backupWarningOwnsBackAndSelect() {
        showDialog { dismiss, action -> BackupExportWarningDialog(action, dismiss) }
        checkDismissal(R.string.common_cancel)
    }

    @Test fun dialogBackWaitsForAnUncancelledKeyUpAndDismissesOnlyOnce() {
        showDialog { dismiss, action -> BackupExportWarningDialog(action, dismiss) }
        assertRegistered()
        rule.runOnIdle {
            assertEquals(true, registry.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
            assertEquals(0, dismissed)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
            registry.dispatchToDialog(KeyEvent.changeFlags(up, KeyEvent.FLAG_CANCELED))
        }
        rule.waitForIdle()
        assertEquals(0, dismissed)
        assertEquals(0, underlyingBack)
        send(RemoteCommand.BACK)
        assertEquals(1, dismissed)
        assertEquals(0, underlyingBack)
        assertEquals(0, underlyingSelect)
        assertReleased()
    }

    @Test fun diagnosticsConsentOwnsBackAndSelect() {
        showDialog { dismiss, action -> DiagnosticsPreviewDialog("Public test report", dismiss, action) }
        checkDismissal(R.string.diagnostics_preview_cancel)
    }

    @Test fun topDialogDismissalRestoresOlderDialogRouting() {
        val outer = mutableStateOf(true)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val input = remember { TvInputRegistry() }.also { registry = it }
                TvPresentation(true, input) {
                    BackHandler { underlyingBack++ }
                    if (outer.value) TrackPickerDialog("Outer dialog", emptyList(), "Off",
                        { actions++; outer.value = false }, {}, { outer.value = false })
                    if (showing.value) BackupExportWarningDialog({}, { dismissed++; showing.value = false })
                }
            }
        }
        send(RemoteCommand.BACK)
        assertEquals(1, dismissed)
        assertEquals(0, underlyingBack)
        assertRegistered()
        val off = rule.onNodeWithText("Off")
        off.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        off.assertIsFocused()
        send(RemoteCommand.SELECT)
        assertEquals(1, actions)
        assertReleased()
    }

    @Test fun resetConfirmationDoesNotNavigateUnderlyingSettings() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val input = remember { TvInputRegistry() }.also { registry = it }
                TvPresentation(true, input) {
                    BackHandler { underlyingBack++ }
                    Column {
                        ParentalControlSection(PlaylistUiState(), emptySet(), true, { true },
                            { actions++ }, {}, { it() })
                    }
                }
            }
        }
        rule.onNodeWithText(rule.activity.getString(R.string.parental_control_reset)).performClick()
        assertRegistered()
        send(RemoteCommand.BACK)
        rule.onNodeWithText(rule.activity.getString(R.string.parental_control_reset_confirm_title))
            .assertDoesNotExist()
        assertEquals(0, underlyingBack)
        assertEquals(0, actions)
        assertReleased()
    }

    private fun showDialog(content: @Composable (() -> Unit, () -> Unit) -> Unit) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val input = remember { TvInputRegistry() }.also { registry = it }
                TvPresentation(true, input) {
                    BackHandler { underlyingBack++ }
                    Box(Modifier.fillMaxSize()) {
                        SecondaryButton("Underlying action", { underlyingSelect++ })
                    }
                    if (showing.value) content({ dismissed++; showing.value = false }, { actions++ })
                }
            }
        }
    }

    private fun checkDismissal(cancelLabel: Int) {
        assertRegistered()
        send(RemoteCommand.BACK)
        assertEquals(1, dismissed)
        assertEquals(0, underlyingBack)
        assertEquals(0, underlyingSelect)
        assertReleased()
        rule.runOnIdle { showing.value = true }
        val cancel = rule.onNodeWithText(rule.activity.getString(cancelLabel))
        cancel.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        cancel.assertIsFocused()
        send(RemoteCommand.SELECT)
        assertEquals(2, dismissed)
        assertEquals(0, actions)
        assertEquals(0, underlyingBack)
        assertEquals(0, underlyingSelect)
        assertReleased()
    }

    private fun assertRegistered() = rule.runOnIdle {
        assertNotNull(registry.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN)))
    }

    private fun assertReleased() = rule.runOnIdle {
        assertNull(registry.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN)))
    }

    private fun send(command: RemoteCommand) {
        rule.runOnIdle { rule.activity.dispatchTvRemote(command, registry::dispatchToDialog) }
        rule.waitForIdle()
    }
}
