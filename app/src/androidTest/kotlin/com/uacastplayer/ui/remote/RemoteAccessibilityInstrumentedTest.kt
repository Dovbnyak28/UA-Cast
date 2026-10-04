package com.uacastplayer.ui.remote

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.R
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.remote.PhoneRemoteState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated UI/semantics fixture: no actual TV connection or saved source/preference mutation. */
@RunWith(AndroidJUnit4::class)
class RemoteAccessibilityInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun debugOnly() { assertEquals("com.uacastplayer.debug", context.packageName) }

    @Test fun modeSelectionKeepsAUsableSeventyTwoDpDpadAndDoesNotReconnect() {
        val mode = mutableStateOf(PhoneRemoteMode.REMOTE)
        val commands = mutableListOf<RemoteCommand>()
        var connections = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PhoneRemoteDialog(mode.value, { mode.value = it }, PhoneRemoteState(connected = true),
                    { _, _ -> connections++ }, { commands.add(it); true }, {})
            }
        }
        rule.onNodeWithText(context.getString(R.string.remote_mode_buttons)).assertIsEnabled().assertIsSelected()
        listOf(RemoteCommand.UP, RemoteCommand.DOWN, RemoteCommand.LEFT, RemoteCommand.RIGHT, RemoteCommand.SELECT)
            .forEach { command ->
                val control = rule.onNodeWithTag("remote_${command.name}").performScrollTo()
                val bounds = control.fetchSemanticsNode().boundsInRoot
                val minimum = 72f * context.resources.displayMetrics.density - 1f
                assertTrue("D-pad targets must remain 72dp", bounds.width >= minimum && bounds.height >= minimum)
                control.performClick()
            }
        assertEquals(listOf(RemoteCommand.UP, RemoteCommand.DOWN, RemoteCommand.LEFT,
            RemoteCommand.RIGHT, RemoteCommand.SELECT), commands)
        rule.onNodeWithText(context.getString(R.string.remote_mode_touchpad)).performScrollTo().performClick()
            .assertIsEnabled().assertIsSelected()
        assertEquals(0, connections)
    }

    @Test fun accessibleTouchpadSelectionAndDirectionsAreDeliveredOnce() {
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
        assertEquals(listOf(R.string.remote_up, R.string.remote_down, R.string.remote_left, R.string.remote_right)
            .map(context::getString), actions.map { it.label })
        rule.runOnIdle { actions.forEach { assertTrue(it.action()) } }
        assertEquals(listOf(RemoteCommand.SELECT, RemoteCommand.UP, RemoteCommand.DOWN,
            RemoteCommand.LEFT, RemoteCommand.RIGHT), commands)
    }
}
