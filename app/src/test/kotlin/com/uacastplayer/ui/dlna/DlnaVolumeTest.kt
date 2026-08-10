package com.uacastplayer.ui.dlna

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.dlna.DlnaDevice
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

/**
 * The DLNA volume control, driven through the sheet rather than the row, because half of what it has
 * to get right is *whether the control is there at all*.
 *
 * A renderer that advertises no RenderingControl service, and a first read that failed, both arrive
 * as `volume = null`. Rendering that as a slider at zero would tell the user the TV is muted - and
 * they would act on it, dragging up a control that talks to nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class DlnaVolumeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val samsung = DlnaDevice(
        friendlyName = "[TV] Samsung 6 Series (40)",
        controlUrl = "http://192.168.1.7:9197/upnp/control/AVTransport1",
        renderingControlUrl = "http://192.168.1.7:9197/upnp/control/RenderingControl1",
    )

    private val volumeLabel = "Гучність телевізора"

    @Test
    fun aRendererWithNoReadableVolumeGetsNoSlider() {
        setSheet(DlnaConnectionState(connectedDevice = samsung, volume = null))

        composeRule.onNodeWithContentDescription(volumeLabel).assertDoesNotExist()
    }

    @Test
    fun theRenderersOwnVolumeIsShownAsANumberNotJustAThumbPosition() {
        setSheet(DlnaConnectionState(connectedDevice = samsung, volume = 23))

        composeRule.onNodeWithContentDescription(volumeLabel).assertIsDisplayed()
        composeRule.onNodeWithText("23%").assertIsDisplayed()
    }

    /** Nothing is connected, so there is no renderer whose volume this could be. */
    @Test
    fun thereIsNoVolumeControlWithoutAConnection() {
        setSheet(DlnaConnectionState(connectedDevice = null, volume = 40))

        composeRule.onNodeWithContentDescription(volumeLabel).assertDoesNotExist()
    }

    /**
     * The value that leaves the control is a whole number on the 0-100 scale the SOAP action takes.
     * A slider works in floats, and `DesiredVolume>41.7` is a fault, not a volume.
     */
    @Test
    fun movingTheSliderReportsAWholeNumberOnTheUpnpScale() {
        var sent: Int? = null
        setSheet(DlnaConnectionState(connectedDevice = samsung, volume = 23)) { sent = it }

        composeRule.onNodeWithContentDescription(volumeLabel)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(41.7f) }

        assertEquals(42, sent)
    }

    /**
     * The repository publishes the value it is about to send before the LAN round trip, and then the
     * one the renderer reports (see `DlnaSessionRepository.setVolume`). This is the second half of
     * that contract: the control must follow the published value rather than keep showing where the
     * finger stopped, or a renderer that clamped the request to its own smaller scale would be
     * misreported for as long as the sheet stayed open.
     */
    @Test
    fun theControlFollowsWhatTheRendererReportedRatherThanWhereTheFingerStopped() {
        val state = androidx.compose.runtime.mutableStateOf(
            DlnaConnectionState(connectedDevice = samsung, volume = 23),
        )
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                DlnaDeviceSheetContent(
                    connectionState = state.value,
                    devices = emptyList(),
                    searching = false,
                    onDeviceSelected = {},
                    onStopCasting = {},
                    onVolumeChange = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(volumeLabel)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        // What a renderer whose real maximum is 30 answers to a request for 90.
        state.value = state.value.copy(volume = 30)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("30%").assertIsDisplayed()
    }

    private fun setSheet(state: DlnaConnectionState, onVolumeChange: (Int) -> Unit = {}) {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                DlnaDeviceSheetContent(
                    connectionState = state,
                    devices = emptyList(),
                    searching = false,
                    onDeviceSelected = {},
                    onStopCasting = {},
                    onVolumeChange = onVolumeChange,
                )
            }
        }
    }
}
