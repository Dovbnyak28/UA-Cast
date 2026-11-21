package com.uacastplayer.ui.dlna

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class DlnaVolumeInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val device = DlnaDevice("TV", "http://192.0.2.1/control")
    private val state = mutableStateOf(DlnaConnectionState(connectedDevice = device, volume = 20))
    private val sent = mutableListOf<Int>()
    private val slider get() = rule.onNodeWithContentDescription("TV volume")

    @Test fun delayedReadbackDoesNotDiscardTheNextDragOrItsCommand() {
        render()
        startDrag()
        val dragged = shownVolume()
        assertTrue("The gesture must move the thumb", dragged > 60f)
        rule.runOnIdle { state.value = state.value.copy(volume = 30) }
        rule.waitForIdle()
        assertEquals("Readback must not steal an active gesture", dragged, shownVolume(), 0.01f)
        slider.performTouchInput { up() }
        rule.runOnIdle { assertEquals(listOf(dragged.roundToInt()), sent) }
    }

    @Test fun switchingRendererDuringADragDoesNotSendOldVolumeToTheNewTv() {
        render()
        startDrag()
        rule.runOnIdle {
            state.value = state.value.copy(connectedDevice = DlnaDevice("Other TV", "http://192.0.2.2/control"))
        }
        rule.waitForIdle()
        slider.performTouchInput { up() }
        rule.runOnIdle { assertTrue("A gesture belongs to the renderer where it started", sent.isEmpty()) }
        assertEquals(20f, shownVolume(), 0.01f)
    }

    private fun startDrag() {
        slider.performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.75f, centerY), delayMillis = 200)
        }
        rule.waitForIdle()
    }

    private fun shownVolume() = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun render() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                DlnaDeviceSheetContent(state.value, emptyList(), false, {}, {}, { sent += it })
            }
        }
    }
}
