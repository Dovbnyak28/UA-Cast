package com.uacastplayer.ui.tv

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.player.TrackPickerDialog
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w960dp-h540dp-television-xhdpi")
@Category(RequiresComposeTestManifest::class)
class TvDialogInputTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `phone back dismisses the dialog instead of reaching the underlying screen`() {
        var registry: TvInputRegistry? = null
        val showing = mutableStateOf(true)
        var dismissed = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val input = remember { TvInputRegistry() }.also { registry = it }
                TvPresentation(true, input) {
                    if (showing.value) TrackPickerDialog("Audio", emptyList(), offLabel = "Off",
                        onSelectOff = {}, onSelect = {},
                        onDismiss = { dismissed++; showing.value = false })
                }
            }
        }
        rule.runOnIdle {
            assertNotNull(registry?.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
            assertNotNull(registry?.dispatchToDialog(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        }
        rule.waitForIdle()
        assertEquals(1, dismissed)
        rule.runOnIdle {
            assertNull(registry?.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        }
    }

    @Test fun `phone input reaches the top dialog and registration is released on dismissal`() {
        var registry: TvInputRegistry? = null
        val showing = mutableStateOf(true)
        var selected = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val input = remember { TvInputRegistry() }.also { registry = it }
                TvPresentation(true, input) {
                    if (showing.value) TrackPickerDialog("Audio", emptyList(), offLabel = "Off",
                        onSelectOff = { selected++; showing.value = false }, onSelect = {}, onDismiss = {})
                }
            }
        }
        rule.onNodeWithText("Off").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithText("Off").assertIsFocused()
        rule.runOnIdle {
            assertNotNull(registry?.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
            assertNotNull(registry?.dispatchToDialog(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
        }
        assertEquals(1, selected)
        rule.waitForIdle()
        rule.runOnIdle {
            assertNull(registry?.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        }
    }
}
