package com.uacastplayer.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.R
import com.uacastplayer.backup.BackupPreview
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.remote.dispatchTvRemote
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.tv.LocalTvMode
import com.uacastplayer.ui.tv.LocalTvInputRegistry
import com.uacastplayer.ui.tv.TvInputRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSecureDialogInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun previewConfirmationReceivesPhoneDpadSelectExactlyOnce() {
        val registry = TvInputRegistry()
        var applied = 0
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            CompositionLocalProvider(LocalTvMode provides true, LocalTvInputRegistry provides registry) {
                BackupPreviewDialog(BackupPreview(2, 3, 1, listOf("bufferSize")), { applied++ }, {})
            }
        } }
        rule.onNodeWithText(rule.activity.getString(R.string.backup_preview_confirm))
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        rule.runOnIdle { rule.activity.dispatchTvRemote(RemoteCommand.SELECT, registry::dispatchToDialog) }
        rule.waitForIdle()
        assertEquals(1, applied)
    }

    @Test fun passwordFieldsCanBeLeftAndCancelledWithPhoneDpad() {
        val registry = TvInputRegistry()
        val showing = mutableStateOf(true)
        var cancelled = 0
        var exported = 0
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            CompositionLocalProvider(LocalTvMode provides true, LocalTvInputRegistry provides registry) {
                if (showing.value) BackupPasswordDialog(true, false, { exported++ }, {
                    cancelled++; showing.value = false
                })
            }
        } }
        rule.onNodeWithText(rule.activity.getString(R.string.backup_password_label))
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        send(RemoteCommand.DOWN, registry)
        rule.onNodeWithText(rule.activity.getString(R.string.backup_password_repeat)).assertIsFocused()
        send(RemoteCommand.DOWN, registry)
        rule.onNodeWithText(rule.activity.getString(R.string.common_cancel)).assertIsFocused()
        send(RemoteCommand.SELECT, registry)
        assertEquals(1, cancelled)
        assertEquals(0, exported)
    }

    @Test fun passwordConfirmationIsMaskedAndRequiresMatchingInput() {
        var exported = 0
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            BackupPasswordDialog(true, false, { exported++ }, {})
        } }
        val password = "Long TV test password"
        rule.onNodeWithText(rule.activity.getString(R.string.backup_password_label)).performTextInput(password)
        rule.onNodeWithText(rule.activity.getString(R.string.backup_password_repeat)).performTextInput(password)
        // InputText intentionally retains raw editable state; displayed EditableText is masked.
        rule.onNodeWithText(rule.activity.getString(R.string.backup_password_label))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,
                AnnotatedString("\u2022".repeat(password.length))))
        rule.onNodeWithText(rule.activity.getString(R.string.settings_data_export_confirm))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertEquals(1, exported)
    }

    private fun send(command: RemoteCommand, registry: TvInputRegistry) {
        rule.runOnIdle { rule.activity.dispatchTvRemote(command, registry::dispatchToDialog) }
        rule.waitForIdle()
    }
}
