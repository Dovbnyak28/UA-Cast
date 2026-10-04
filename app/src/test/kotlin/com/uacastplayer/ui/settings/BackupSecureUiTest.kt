package com.uacastplayer.ui.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.uacastplayer.backup.BackupPreview
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class BackupSecureUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun exportRejectsWeakOrMismatchedPasswordAndClearsCallbackBuffer() {
        var delivered = ""
        var retained: CharArray? = null
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            BackupPasswordDialog(true, false, {
                delivered = String(it)
                retained = it
            }, {})
        } }
        rule.onNodeWithText("Продовжити").assertIsNotEnabled()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("a long test passphrase")
        rule.onNodeWithText("Продовжити").assertIsNotEnabled()
        fields[1].performTextInput("a long test passphrase")
        rule.onNodeWithText("Продовжити").performClick()
        assertEquals("a long test passphrase", delivered)
        assertEquals(true, retained!!.all { it == '\u0000' })
    }

    @Test fun previewShowsOnlyCountsAndCancelDoesNotApply() {
        var applied = 0
        var cancelled = 0
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            BackupPreviewDialog(BackupPreview(2, 3, 1, listOf("bufferSize")), { applied++ }, { cancelled++ })
        } }
        rule.onNodeWithText("Джерел: 2", substring = true).assertExists()
        rule.onNodeWithText("Скасувати").performClick()
        assertEquals(0, applied)
        assertEquals(1, cancelled)
    }
}
