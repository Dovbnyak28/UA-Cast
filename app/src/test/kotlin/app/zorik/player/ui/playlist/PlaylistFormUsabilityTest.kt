package app.zorik.player.ui.playlist

import android.content.ClipData
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlaylistFormUsabilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun clipboardIsReadOnlyAfterAnExplicitTapAndThePastedUrlIsValidated() {
        val clipboard = TestClipboard()
        rule.setContent { ZorikTheme(AppTheme.DAYLIGHT) {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                AddPlaylistScreen(PlaylistUiState(), {}, {}, {}, { _, _, _ -> }, {}, {})
            }
        } }
        rule.runOnIdle { assertEquals(0, clipboard.reads) }
        rule.onNodeWithText("Paste link").performClick()
        rule.onNodeWithText("https://provider.example/list.m3u").assertExists()
        rule.onNodeWithText("Download and save").performScrollTo().assertIsEnabled()
        rule.runOnIdle { assertEquals(1, clipboard.reads) }
    }

    @Test fun returningUsersGetAShortHintAndAnOptionalNameField() {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            AddPlaylistScreen(PlaylistUiState(displayName = "Old source"), {}, {}, {}, { _, _, _ -> }, {}, {},
                hasExistingSources = true)
        } }
        rule.onNodeWithText("Add another source from your TV provider.").assertExists()
        rule.onNodeWithText("Advanced · optional name").performScrollTo().performClick()
        rule.onNodeWithText("Hide advanced").assertExists()
        rule.onNodeWithText("Old source").assertDoesNotExist()
    }

    @Test fun aDelayedPasteCannotOverwriteInputThatWasTypedAndThenCleared() {
        val gate = CompletableDeferred<Unit>()
        val clipboard = TestClipboard(gate)
        rule.setContent { ZorikTheme(AppTheme.DAYLIGHT) {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                AddPlaylistScreen(PlaylistUiState(), {}, {}, {}, { _, _, _ -> }, {}, {})
            }
        } }
        rule.onNodeWithText("Paste link").performClick()
        rule.runOnIdle { assertEquals(1, clipboard.reads) }
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val field = rule.onNodeWithText(context.getString(app.zorik.player.R.string.add_playlist_url_hint))
        field.performTextInput("https://new.example/")
        field.performTextClearance()
        rule.runOnIdle { gate.complete(Unit) }
        rule.onNodeWithText("https://provider.example/list.m3u").assertDoesNotExist()
        rule.onNodeWithText("Download and save").performScrollTo().assertIsNotEnabled()
    }

    private class TestClipboard(private val gate: CompletableDeferred<Unit>? = null) : Clipboard {
        override val nativeClipboard: android.content.ClipboardManager
            get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        var reads = 0
        override suspend fun getClipEntry(): ClipEntry {
            reads++
            gate?.await()
            return ClipEntry(ClipData.newPlainText("source", "  https://provider.example/list.m3u  "))
        }
        override suspend fun setClipEntry(clipEntry: ClipEntry?) = Unit
    }
}
