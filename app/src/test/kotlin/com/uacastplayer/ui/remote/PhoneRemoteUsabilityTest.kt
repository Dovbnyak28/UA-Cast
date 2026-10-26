package com.uacastplayer.ui.remote

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.remote.PhoneRemoteState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PhoneRemoteUsabilityTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun restoreFontScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun selectedModeIsAvailableAndSwitchingDoesNotReconnect() {
        val mode = mutableStateOf(PhoneRemoteMode.REMOTE)
        val commands = mutableListOf<RemoteCommand>()
        var connections = 0
        var modeChanges = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PhoneRemoteDialog(mode.value, { modeChanges++; mode.value = it }, PhoneRemoteState(connected = true),
                    { _, _ -> connections++ }, { commands.add(it); true }, {})
            }
        }
        rule.onNodeWithText(context.getString(R.string.remote_mode_buttons)).assertIsEnabled().assertIsSelected()
            .performClick()
        assertEquals(0, modeChanges)
        rule.onNodeWithTag("remote_SELECT").performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.remote_mode_touchpad)).performScrollTo().performClick()
            .assertIsEnabled().assertIsSelected()
        rule.onNodeWithTag("remote_UP").assertDoesNotExist()
        assertEquals(listOf(RemoteCommand.SELECT), commands)
        assertEquals(0, connections)
        assertEquals(1, modeChanges)
    }

    @Test fun touchpadSupportsAccessibleSelectAndEveryDirectionWithoutDuplicateCommands() {
        val commands = mutableListOf<RemoteCommand>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PhoneRemoteDialog(PhoneRemoteMode.TOUCHPAD, {}, PhoneRemoteState(connected = true),
                    { _, _ -> error("Must not reconnect") }, { commands.add(it); true }, {})
            }
        }
        val pad = rule.onNodeWithTag("remote_touchpad").performScrollTo()
        pad.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        val actions = pad.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle {
            assertEquals(listOf(R.string.remote_up, R.string.remote_down, R.string.remote_left, R.string.remote_right)
                .map(context::getString), actions.map { it.label })
            actions.forEach { assertTrue(it.action()) }
        }
        assertEquals(listOf(RemoteCommand.SELECT, RemoteCommand.UP, RemoteCommand.DOWN,
            RemoteCommand.LEFT, RemoteCommand.RIGHT), commands)
    }

    @Test fun englishModeLabelsFitAtTwoHundredPercent() = assertLargeLabelsFit()

    @Test @Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
    fun ukrainianModeLabelsFitAtTwoHundredPercent() = assertLargeLabelsFit()

    @Test @Config(qualifiers = "ru-w320dp-h480dp-xhdpi")
    fun russianModeLabelsFitAtTwoHundredPercent() = assertLargeLabelsFit()

    @Test @Config(qualifiers = "es-w320dp-h480dp-xhdpi")
    fun spanishModeLabelsFitAtTwoHundredPercent() = assertLargeLabelsFit()

    @Test fun selectLabelFitsAtTwoHundredPercentInNarrowDialog() {
        RuntimeEnvironment.setFontScale(2f)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PhoneRemoteDialog(PhoneRemoteMode.REMOTE, {}, PhoneRemoteState(connected = true),
                    { _, _ -> }, { true }, {})
            }
        }
        assertLabelFits("OK")
    }

    private fun assertLargeLabelsFit() {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, context.resources.configuration.fontScale, 0.01f)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    // Exact usable width of the 320dp dialog after its 16dp outer / 24dp inner padding.
                    Box(Modifier.width(240.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            RemoteModeSelector(PhoneRemoteMode.REMOTE, {})
                        }
                    }
                }
            }
        }
        listOf(R.string.remote_mode_buttons, R.string.remote_mode_touchpad).forEach { label ->
            assertLabelFits(context.getString(label))
        }
    }

    private fun assertLabelFits(label: String) {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(label, useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        val layout = results.single()
        assertEquals("$label must stay on one line", 1, layout.lineCount)
        // Center alignment uses the paragraph's constraint width, not Text's intrinsic width.
        // Compare glyph extent instead of its offset inside that wider paragraph.
        val glyphWidth = layout.getLineRight(0) - layout.getLineLeft(0)
        assertTrue("$label glyphs must fit at 320dp / 200%: size=${layout.size}, " +
            "line=${layout.getLineLeft(0)}..${layout.getLineRight(0)}", glyphWidth <= layout.size.width + 1f)
        assertFalse("Label must not be truncated", layout.isLineEllipsized(0))
        assertFalse("Label must not overflow vertically", layout.didOverflowHeight)
    }
}
