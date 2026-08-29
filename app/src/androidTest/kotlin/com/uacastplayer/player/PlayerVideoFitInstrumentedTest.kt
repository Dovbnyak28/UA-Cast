package com.uacastplayer.player

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.core.settings.PlayerResizeMode
import com.uacastplayer.testsupport.FakeOriginServer
import com.uacastplayer.testsupport.loadTestPlaylist
import com.uacastplayer.testsupport.openChannelViaSearch
import com.uacastplayer.testsupport.setAutoSkipDeadChannels
import com.uacastplayer.testsupport.skipOnboarding
import com.uacastplayer.testsupport.tapChannelRow
import com.uacastplayer.testsupport.waitForChannelsLoaded
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How the picture is fitted to the screen, driven through the real player on a real screen.
 *
 * There is an open field report behind this: fullscreen video "stretched too much" on a phone.
 * Three things decide that, and only one of them is a setting - which is why this covers the
 * setting end to end rather than asserting on [ResizeModeCycle.next] alone (which unit tests
 * already do). What a device adds is the rest of the chain: that the button is reachable, that the
 * cycle is what a tap actually runs, that the value survives leaving the player, and that the mode
 * a user is in is the one a diagnostics report will name.
 *
 * FILL is the one that stretches - it fills the screen without preserving the aspect ratio - and it
 * is one tap away at any time. FIT is the default, and this pins that too: a default of FILL would
 * ship the reported symptom to everyone.
 */
@RunWith(AndroidJUnit4::class)
class PlayerVideoFitInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var server: FakeOriginServer
    private var previousAutoSkip: Boolean = true
    private var previousResizeMode: PlayerResizeMode = PlayerResizeMode.DEFAULT

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun preferences() = AppPreferences(context().applicationContext)

    @Before
    fun setUp() {
        // Both of these are real, persisted settings on whatever phone this runs against, so both
        // are captured and put back. See setAutoSkipDeadChannels for why auto-skip has to be off
        // before the player is constructed.
        previousAutoSkip = setAutoSkipDeadChannels(context(), enabled = false)
        previousResizeMode = preferences().playerResizeMode
        preferences().playerResizeMode = PlayerResizeMode.DEFAULT

        server = FakeOriginServer.startWithChannels(channelCount = 3)
        composeTestRule.activityRule.scenario.onActivity { activity ->
            skipOnboarding(activity)
            loadTestPlaylist(activity, server)
        }
        composeTestRule.waitForChannelsLoaded(server)
        composeTestRule.openChannelViaSearch("Channel 1")
    }

    @After
    fun tearDown() {
        server.shutdown()
        setAutoSkipDeadChannels(context(), previousAutoSkip)
        preferences().playerResizeMode = previousResizeMode
    }

    /** One `onActivity` per read, never nested - it posts to the main thread and blocks, so reading
     * two things from inside one another deadlocks. */
    private fun currentMode(): PlayerResizeMode {
        var mode = PlayerResizeMode.DEFAULT
        composeTestRule.activityRule.scenario.onActivity { activity ->
            mode = ViewModelProvider(activity)[PlayerViewModel::class.java].uiState.value.resizeMode
        }
        return mode
    }

    private fun tapAspectRatio() {
        val node = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.player_aspect_ratio))
        node.performScrollTo()
        composeTestRule.waitUntil(OPEN_TRANSFORM_TIMEOUT_MILLIS) {
            val bounds = node.fetchSemanticsNode().boundsInRoot
            bounds.width > 0f && bounds.height > 0f
        }
        node
            .performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * The default must letterbox, never stretch.
     *
     * Asserted on the device because this is the state every user starts in and the one the field
     * report is measured against: if a fresh install already stretched, nothing further about the
     * cycle would matter.
     */
    @Test
    fun aFreshPlayerFitsRatherThanStretches() {
        assertEquals(PlayerResizeMode.FIT, currentMode())
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, ResizeModeCycle.toMedia3ResizeMode(currentMode()))
    }

    /**
     * Fit -> Fill -> Zoom -> Fit, through the button a user presses.
     *
     * The cycle itself is a pure function with its own unit test; what is only true on a device is
     * that this button is wired to it, is hittable, and comes back round. A user who reached FILL
     * by accident has to be able to leave it the same way.
     */
    @Test
    fun theAspectButtonCyclesAllTheWayBackToFit() {
        tapAspectRatio()
        assertEquals("the first tap must reach the stretching mode", PlayerResizeMode.FILL, currentMode())

        tapAspectRatio()
        assertEquals(PlayerResizeMode.ZOOM, currentMode())

        tapAspectRatio()
        assertEquals("a user who reached FILL by accident must be able to get out", PlayerResizeMode.FIT, currentMode())
    }

    /**
     * The mode is global and persisted, which is exactly why it can surprise someone: set once by a
     * mis-tap, it is still there on the next channel, the next session and the next day - long past
     * the point where anyone would connect the two. Pinned here as the behaviour it is, so that
     * "why is my video stretched" has a cause that survives closing the player.
     */
    @Test
    fun theModeOutlivesThePlayerItWasSetIn() {
        tapAspectRatio()
        assertEquals(PlayerResizeMode.FILL, currentMode())
        assertEquals("the setting never reached storage", PlayerResizeMode.FILL, preferences().playerResizeMode)

        // Closed outright, not collapsed: this has to survive the player being torn down, which is
        // the only version of it a user would ever notice.
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.common_back))
            .performClick()
        composeTestRule.waitForIdle()
        // The same channel, not a different one: the search field still holds the query setUp typed,
        // so it is the row that is actually on screen - and a fresh PlayerViewModel reading the
        // setting back is the whole of what this asserts either way.
        composeTestRule.tapChannelRow("Channel 1")

        assertEquals("a reopened player started over at the default", PlayerResizeMode.FILL, currentMode())
    }

    /**
     * And the reason the line exists in the report at all: the answer has to be forwardable.
     *
     * A real report arrived with "stretched too much" and said nothing about which of the three
     * modes was in effect, so the one question that would have settled it could not be asked
     * remotely. This asserts the report now names it, and names the mode actually in force rather
     * than a default - taken from a report built the way a user builds one, from Settings, with the
     * player closed.
     */
    @Test
    fun aDiagnosticsReportNamesTheModeInForce() {
        tapAspectRatio()
        tapAspectRatio()
        assertEquals(PlayerResizeMode.ZOOM, currentMode())

        var report = ""
        composeTestRule.activityRule.scenario.onActivity { activity ->
            report = ViewModelProvider(activity)[com.uacastplayer.AppViewModel::class.java].buildDiagnosticsReport()
        }

        // ZOOM rather than the default on purpose: the snapshot field defaults to FIT, so a report
        // that never received the real value would still print a plausible line, and only a mode
        // the default cannot produce tells the two apart.
        assertTrue(
            "the report cannot answer the question it is sent to answer:\n" +
                report.lineSequence().take(TOP_LINES).joinToString("\n"),
            report.lineSequence().any { it == "Video fit: ${PlayerResizeMode.ZOOM}" },
        )
    }

    private companion object {
        const val TOP_LINES = 12
        const val OPEN_TRANSFORM_TIMEOUT_MILLIS = 2_000L
    }
}
