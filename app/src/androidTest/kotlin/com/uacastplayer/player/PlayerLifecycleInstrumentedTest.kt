package com.uacastplayer.player

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.testsupport.FakeOriginServer
import com.uacastplayer.testsupport.loadTestPlaylist
import com.uacastplayer.testsupport.openChannelViaSearch
import com.uacastplayer.testsupport.skipOnboarding
import com.uacastplayer.testsupport.tapChannelRow
import com.uacastplayer.testsupport.waitForChannelsLoaded
import com.uacastplayer.ui.UiTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented regression coverage for the double-ExoPlayer leak (see the Session A fix in
 * [PlayerViewModel] and [com.uacastplayer.ui.player.PlayerHost]): every scenario here is a
 * lifecycle transition that used to be able to spin up a second [PlayerViewModel] - and therefore
 * a second ExoPlayer - while the first was still alive. [PlayerViewModel.liveInstanceCountForTest]
 * is the same counter that guards this in production; these tests just assert it never exceeds 1.
 *
 * Not run in CI (no emulator there) - see android-ci.yml, which only compiles this module via
 * :app:assembleDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class PlayerLifecycleInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var server: FakeOriginServer

    @Before
    fun setUp() {
        server = FakeOriginServer.startWithChannels(channelCount = 3)
        composeTestRule.activityRule.scenario.onActivity { activity ->
            skipOnboarding(activity)
            loadTestPlaylist(activity, server)
        }
        composeTestRule.waitForChannelsLoaded()
        composeTestRule.openChannelViaSearch("Channel 1")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun backButton(): SemanticsNodeInteraction =
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.common_back))

    /** Scenario 1: open -> close, 10 times. Checked after every cycle, not just at the end - a
     * regression that only shows up on, say, the 7th reopen must not be missed. */
    @Test
    fun openCloseTenCycles_neverLeaksASecondInstance() {
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
        backButton().performClick() // close outright (Event.Close)
        composeTestRule.waitForIdle()

        repeat(9) {
            composeTestRule.tapChannelRow("Channel 1") // reopen (Event.Open)
            assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
            backButton().performClick() // close (Event.Close)
            composeTestRule.waitForIdle()
        }
    }

    /** Scenario 2: mini <-> fullscreen, 10 times, via the system back gesture (collapses) and a
     * tap on the mini bar (expands) - the actual affordances a user has, not a direct state-machine
     * call. ASSERT: same [PlayerViewModel] identity throughout, never more than one live instance. */
    @Test
    fun miniFullscreenToggleTenTimes_sameInstanceThroughout() {
        val initialInstance = composeTestRule.activityRule.scenario.let { scenario ->
            var vm: PlayerViewModel? = null
            scenario.onActivity { vm = ViewModelProvider(it)[PlayerViewModel::class.java] }
            checkNotNull(vm)
        }

        repeat(10) {
            Espresso.pressBack() // collapse to mini (Event.Back: EXPANDED -> COLLAPSED)
            composeTestRule.waitForIdle()
            assertEquals(1, PlayerViewModel.liveInstanceCountForTest())

            composeTestRule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).performClick() // expand (Event.Tap)
            composeTestRule.waitForIdle()

            composeTestRule.activityRule.scenario.onActivity { activity ->
                assertTrue(
                    "PlayerViewModel identity changed across a mini/fullscreen toggle - this is the leak",
                    ViewModelProvider(activity)[PlayerViewModel::class.java] === initialInstance,
                )
            }
        }
    }

    /** Scenario 3: a configuration change (rotation) while playing must retain both the
     * [PlayerViewModel] instance and the channel that was loaded - it must NOT be the trigger that
     * creates a second one, which is exactly how the original OOM leak manifested. */
    @Test
    fun configurationChange_retainsInstanceAndChannel() {
        var instanceBeforeRecreate: PlayerViewModel? = null
        composeTestRule.activityRule.scenario.onActivity { activity ->
            instanceBeforeRecreate = ViewModelProvider(activity)[PlayerViewModel::class.java]
        }

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        composeTestRule.activityRule.scenario.onActivity { activity ->
            val instanceAfterRecreate = ViewModelProvider(activity)[PlayerViewModel::class.java]
            assertTrue(
                "A configuration change must retain the same PlayerViewModel, not create a new one",
                instanceAfterRecreate === instanceBeforeRecreate,
            )
            assertEquals("Channel 1", instanceAfterRecreate.uiState.value.currentChannel?.displayName)
        }
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
    }

    /** Scenario 4: rapid channel switching must reuse the one player, never spin up another one,
     * and never throw. */
    @Test
    fun tenChannelSwitches_reuseSingleInstanceWithoutThrowing() {
        val nextLabel = composeTestRule.activity.getString(R.string.player_next)
        repeat(10) {
            composeTestRule.onNodeWithContentDescription(nextLabel).performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(SWITCH_SETTLE_MILLIS) // let PlayerViewModel's switch debounce (220ms) resolve
        }
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
    }

    private companion object {
        const val SWITCH_SETTLE_MILLIS = 300L
    }
}
