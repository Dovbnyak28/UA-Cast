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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.uacastplayer.R
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.Feature
import com.uacastplayer.remote.dispatchTvRemote
import com.uacastplayer.testing.RequiresComposeTestManifest
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
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the production Activity dispatcher, not a direct dialog click. No app data is used.
 * PIN creation is covered by TvConfirmationDialogInstrumentedTest: the local Robolectric runtime
 * cannot reach idle for its two-text-field dialog, including with explicit virtual frame advances. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w960dp-h540dp-television-xhdpi")
@Category(RequiresComposeTestManifest::class)
class TvConfirmationDialogInputTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val showing = mutableStateOf(true)
    private lateinit var registry: TvInputRegistry
    private var dismissed = 0
    private var actions = 0
    private var underlyingBack = 0
    private var underlyingSelect = 0

    @Test fun `premium unlock routes phone back and select to its own window`() {
        showDialog { dismiss, action -> UnlockDialog(Feature.BACKUP, action, dismiss) }
        checkDismissal(R.string.premium_unlock_later)
    }

    @Test fun `backup warning routes phone back and select to its own window`() {
        showDialog { dismiss, action -> BackupExportWarningDialog(action, dismiss) }
        checkDismissal(R.string.common_cancel)
    }

    @Test fun `dialog back waits for an uncancelled key up and dismisses only once`() {
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

    @Test fun `diagnostics consent routes phone back and select to its own window`() {
        showDialog { dismiss, action -> DiagnosticsPreviewDialog("Public test report", dismiss, action) }
        checkDismissal(R.string.diagnostics_preview_cancel)
    }

    @Test fun `backup approval executes once through the phone dispatcher`() {
        showDialog { dismiss, action -> BackupExportWarningDialog(action, dismiss) }
        checkAction(R.string.settings_data_export_confirm)
    }

    @Test fun `diagnostics approval executes once through the phone dispatcher`() {
        showDialog { dismiss, action -> DiagnosticsPreviewDialog("Public test report", dismiss, action) }
        checkAction(R.string.diagnostics_preview_send)
    }

    @Test fun `premium approval executes once through the phone dispatcher`() {
        showDialog { dismiss, action -> UnlockDialog(Feature.BACKUP, action, dismiss) }
        checkAction(R.string.premium_unlock_cta)
    }

    @Test fun `closing the top confirmation restores routing to the older dialog`() {
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

    @Test fun `parental reset confirmation consumes phone back without navigating settings`() {
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
                    if (showing.value) content(
                        { dismissed++; showing.value = false },
                        { actions++; showing.value = false },
                    )
                }
            }
        }
        settle()
    }

    private fun checkDismissal(cancelLabel: Int) {
        assertRegistered()
        send(RemoteCommand.BACK)
        assertEquals(1, dismissed)
        assertEquals(0, underlyingBack)
        assertEquals(0, underlyingSelect)
        assertReleased()
        rule.runOnIdle { showing.value = true }
        settle()
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

    private fun checkAction(label: Int) {
        assertRegistered()
        val button = rule.onNodeWithText(rule.activity.getString(label))
        button.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        button.assertIsFocused()
        send(RemoteCommand.SELECT)
        assertEquals(1, actions)
        assertEquals(0, dismissed)
        assertEquals(0, underlyingBack)
        assertReleased()
    }

    private fun assertRegistered() = rule.runOnIdle {
        assertNotNull("A modal must intercept phone keys before the Activity",
            registry.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN)))
    }

    private fun assertReleased() = rule.runOnIdle {
        assertNull(registry.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN)))
    }

    private fun send(command: RemoteCommand) {
        rule.runOnIdle { rule.activity.dispatchTvRemote(command, registry::dispatchToDialog) }
        settle()
    }

    private fun settle() {
        rule.waitForIdle()
    }
}
