package com.uacastplayer.player

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.testsupport.FakeOriginServer
import com.uacastplayer.testsupport.loadTestPlaylist
import com.uacastplayer.testsupport.openChannelViaSearch
import com.uacastplayer.testsupport.setAutoSkipDeadChannels
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
import kotlin.time.Duration.Companion.seconds

/**
 * Instrumented regression coverage for the double-ExoPlayer leak (see the Session A fix in
 * [PlayerViewModel] and [com.uacastplayer.ui.player.PlayerHost]): every scenario here is a
 * lifecycle transition that used to be able to spin up a second [PlayerViewModel] - and therefore
 * a second ExoPlayer - while the first was still alive. [PlayerViewModel.liveInstanceCountForTest]
 * is the same counter that guards this in production; these tests just assert it never exceeds 1.
 *
 * Compiled by the fast CI job and actually run by its `instrumented` job on an emulator, the
 * same way scripts/run-instrumented-tests.sh runs them by hand - see android-ci.yml.
 */
@RunWith(AndroidJUnit4::class)
class PlayerLifecycleInstrumentedTest {
    @Test fun sleepExpiryCancelsDeferredForegroundResumeUntilExplicitPlay() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[PlayerViewModel::class.java]
            // The empty synthetic HLS may have already reached its terminal error screen.
            // Expire during a fresh preparation; terminal errors correctly require Retry, not Play.
            model.start(listOf(checkNotNull(model.uiState.value.currentChannel)), 0)
            assertTrue(!model.uiState.value.fatalError)
            model.onEnterBackground(false)
            model.sleepTimer.start(kotlin.time.Duration.ZERO)
        }
        composeTestRule.waitForIdle()
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[PlayerViewModel::class.java]
            model.onReturnToForeground()
            assertTrue(!model.player.playWhenReady)
            assertEquals(androidx.media3.common.Player.STATE_IDLE, model.player.playbackState)
            assertEquals(null, model.sleepTimer.remainingMillis.value)
            model.togglePlayback()
            assertTrue(model.player.playWhenReady)
        }
    }

    @Test fun existingPlayerObservesNavigationPreferenceChanges() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val preferences = com.uacastplayer.data.prefs.AppPreferences(activity)
            val originalWrap = preferences.wrapAroundEnabled
            val originalSkip = preferences.autoSkipDeadEnabled
            try {
                val model = ViewModelProvider(activity)[PlayerViewModel::class.java]
                preferences.wrapAroundEnabled = false
                assertTrue(!model.uiState.value.canGoPrevious)
                preferences.wrapAroundEnabled = true
                assertTrue(model.uiState.value.canGoPrevious)
                preferences.autoSkipDeadEnabled = !originalSkip
                assertEquals(!originalSkip, model.autoSkipDeadEnabled)
                val first = checkNotNull(model.uiState.value.currentChannel)
                model.start(listOf(first, first.copy(displayName = "Last")), 1)
                assertTrue(model.uiState.value.nextChannelsPreview.isNotEmpty())
                preferences.wrapAroundEnabled = false
                assertTrue(model.uiState.value.nextChannelsPreview.isEmpty())
                assertTrue(!model.uiState.value.canGoNext)
            } finally {
                preferences.wrapAroundEnabled = originalWrap
                preferences.autoSkipDeadEnabled = originalSkip
            }
        }
    }
    @Test fun bufferingPauseAndRapidTogglesFollowEngineIntent() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[PlayerViewModel::class.java]
            model.player.playWhenReady = true
            model.start(listOf(com.uacastplayer.playlist.M3uChannel("Preparing", server.playlistUrl())), 0)
            // The playback thread cannot finish preparation inside this Main-thread transaction.
            assertTrue(model.player.playWhenReady)
            assertTrue(!model.player.isPlaying)
            assertTrue(model.uiState.value.wantsToPlay)
            assertTrue(PlaybackActivity.isActive.value)
            model.togglePlayback()
            assertTrue(!model.player.playWhenReady)
            assertTrue(!model.uiState.value.wantsToPlay)
            assertTrue(!PlaybackActivity.isActive.value)
            repeat(30) { model.togglePlayback() }
            assertTrue(!model.player.playWhenReady)
            model.releasePlayback()
            model.togglePlayback()
            assertTrue(!model.uiState.value.canControlPlayback)
            assertTrue(!PlaybackActivity.isActive.value)
        }
    }

    @Test fun localToggleCannotStealPlaybackFromEitherRemoteProtocol() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[PlayerViewModel::class.java]
            model.player.pause()
            model.setRemoteCastingForLifecycleTest(chromecast = false, dlna = true)
            repeat(5) { model.togglePlayback() }
            assertTrue(!model.player.playWhenReady)
            assertTrue(!model.uiState.value.canControlPlayback)
            assertTrue(PlaybackActivity.isActive.value)
            model.setRemoteCastingForLifecycleTest(chromecast = true, dlna = false)
            model.togglePlayback()
            assertTrue(!model.player.playWhenReady)
            model.setRemoteCastingForLifecycleTest(chromecast = false, dlna = false)
        }
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var server: FakeOriginServer
    private var previousAutoSkip: Boolean = true

    @Before
    fun setUp() {
        // Before the player is ever opened - see setAutoSkipDeadChannels for why these tests cannot
        // run with auto-skip on, and why PlayerViewModel has to be constructed after this.
        previousAutoSkip = setAutoSkipDeadChannels(
            InstrumentationRegistry.getInstrumentation().targetContext,
            enabled = false,
        )
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
        // A real, persisted setting on whatever device this ran against - put it back.
        setAutoSkipDeadChannels(InstrumentationRegistry.getInstrumentation().targetContext, previousAutoSkip)
    }

    private fun backButton(): SemanticsNodeInteraction =
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.common_back))

    private fun closePlayerFromScreen() {
        backButton().performClick()
        composeTestRule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).assertExists()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.player_mini_close),
        ).performClick()
    }

    @Test
    fun onScreenBackCollapsesJustLikeSystemBack_andCloseReleasesPlayback() {
        var original: PlayerViewModel? = null
        composeTestRule.activityRule.scenario.onActivity { original = ViewModelProvider(it)[PlayerViewModel::class.java] }
        backButton().performClick()
        composeTestRule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).assertExists()
        composeTestRule.activityRule.scenario.onActivity {
            val current = ViewModelProvider(it)[PlayerViewModel::class.java]
            assertTrue(original === current)
            assertEquals("Channel 1", current.uiState.value.currentChannel?.displayName)
        }
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.player_mini_close),
        ).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).assertDoesNotExist()
        composeTestRule.activityRule.scenario.onActivity {
            assertEquals(null, ViewModelProvider(it)[PlayerViewModel::class.java].uiState.value.currentChannel)
        }
    }

    /** Scenario 1: open -> close, 10 times. Checked after every cycle, not just at the end - a
     * regression that only shows up on, say, the 7th reopen must not be missed. */
    @Test
    fun openCloseTenCycles_neverLeaksASecondInstance() {
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
        closePlayerFromScreen()
        composeTestRule.waitForIdle()

        repeat(9) {
            composeTestRule.tapChannelRow("Channel 1") // reopen (Event.Open)
            assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
            closePlayerFromScreen()
            composeTestRule.waitForIdle()
        }
    }

    /**
     * Whether the player intends to play, which is what [BackgroundPlaybackPolicy] acts on.
     *
     * Deliberately not `isPlaying`. [FakeOriginServer] serves a valid but empty HLS VOD - enough for
     * a data source to complete cleanly, with no frame a decoder could ever render - so `isPlaying`
     * is false throughout this suite no matter what the app does. Asserting on it made these tests
     * unpassable by construction. `playWhenReady` is the flag pause() and play() move, so it is both
     * the honest signal here and the one production reads.
     *
     * One `onActivity` per read, never nested: it posts to the main thread and blocks until it
     * returns, so calling it from inside another one deadlocks - which is precisely what happened.
     */
    private fun wantsToPlay(): Boolean {
        var wants = false
        composeTestRule.activityRule.scenario.onActivity { activity ->
            wants = ViewModelProvider(activity)[PlayerViewModel::class.java].player.playWhenReady
        }
        return wants
    }

    @Test
    fun sleepTimerSurvivesCollapsingThePlayer() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[PlayerViewModel::class.java]
            viewModel.player.play()
            viewModel.sleepTimer.start(3.seconds)
        }
        Espresso.pressBack()
        composeTestRule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).assertExists()
        composeTestRule.waitUntil(8_000) { !wantsToPlay() }
        composeTestRule.activityRule.scenario.onActivity { activity ->
            assertEquals(null, ViewModelProvider(activity)[PlayerViewModel::class.java].sleepTimer.remainingMillis.value)
        }
    }

    /**
     * Leaving the app stops local playback, and coming back starts it again.
     *
     * This is the wiring half of [BackgroundPlaybackPolicy] - the policy's own tests say what the
     * answer should be, and only this says that anything is asking. Without the observer in
     * [com.uacastplayer.ui.player.PlayerHost] the policy is a correct function nobody calls, which
     * is exactly the state the app shipped in: Home left an IPTV stream running from a stopped
     * activity, holding a wake lock, with no notification to stop it.
     *
     * `moveToState(CREATED)` is what the framework does for Home, Recent Apps, an app switch and a
     * screen lock alike - all four arrive as ON_STOP.
     */
    @Test
    fun leavingAndReturningPausesAndResumesPlayback() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) { wantsToPlay() }

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.waitForIdle()
        assertTrue("playback must not continue from a stopped activity", !wantsToPlay())

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(timeoutMillis = 10_000) { wantsToPlay() }
    }

    /**
     * A rotation must not be mistaken for leaving the app.
     *
     * It reaches ON_STOP on its way to rebuilding the Activity, so a background-pause that did not
     * exclude it would stall a live channel every time the phone turned - the same reason
     * `releasePlayback()` is guarded on `isChangingConfigurations`. Asserted by never observing a
     * paused player across the recreate, not merely by checking the end state, which would be
     * identical either way.
     */
    @Test
    fun rotatingDoesNotPausePlayback() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) { wantsToPlay() }

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        assertTrue("a rotation is not the app leaving the screen", wantsToPlay())
    }

    /** And the other half of the rule: a channel the user paused themselves stays paused. Getting
     * this wrong would be worse than the bug, because it would fire on every return to the app
     * rather than only on backgrounding. */
    @Test
    fun aChannelPausedByTheUserIsNotResumedOnReturn() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) { wantsToPlay() }
        composeTestRule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[PlayerViewModel::class.java].player.pause()
        }
        composeTestRule.waitForIdle()

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()

        assertTrue("returning to the app must not undo the user's pause", !wantsToPlay())
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

        // A retained VM alone is insufficient: the old saved-state mirror erased the player
        // request on recreation, leaving the engine alive with no PlayerHost/video surface. The
        // fixture intentionally serves an empty HLS stream, which puts the player into its
        // terminal-error card and hides the ordinary fullscreen toggle. Match the stable exit
        // affordance instead; it proves that PlayerHost is mounted in both success and error UI.
        val playerExit = composeTestRule.activity.getString(R.string.player_back_to_channels)
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasText(playerExit)).fetchSemanticsNodes().isNotEmpty()
        }

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

    /** The persisted restore key must follow a Next/Previous switch, not only the channel that
     * originally opened the player. This catches a subtle rotation/process-death regression that
     * is invisible when the first channel is still current. */
    @Test
    fun configurationChange_retainsMostRecentlySwitchedChannel() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[PlayerViewModel::class.java].navigation.requestSwitch(1)
        }
        composeTestRule.waitUntil(10_000) {
            composeTestRule.activityRule.scenario.run {
                var current: String? = null
                onActivity { activity ->
                    current = ViewModelProvider(activity)[PlayerViewModel::class.java]
                        .uiState.value.currentChannel?.displayName
                }
                current == "Channel 2"
            }
        }

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        val playerExit = composeTestRule.activity.getString(R.string.player_back_to_channels)
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasText(playerExit)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.activityRule.scenario.onActivity { activity ->
            assertEquals(
                "Channel 2",
                ViewModelProvider(activity)[PlayerViewModel::class.java]
                    .uiState.value.currentChannel?.displayName,
            )
        }
    }

    /**
     * Scenario 4: rapid channel switching must reuse the one player, never spin up another one,
     * and never throw.
     *
     * Matched by *text*, not by content description: the player opens inline, and the inline
     * transport row is a `PillButton` whose label is a Text with `contentDescription = null` on its
     * icons. Only the fullscreen overlay's `RoundIconButton` carries "Next" as a content
     * description, so the original lookup could never have matched from this state - it failed with
     * "could not find any node", which reads like the player being gone rather than like the wrong
     * matcher.
     */
    @Test
    fun tenChannelSwitches_reuseSingleInstanceWithoutThrowing() {
        val nextLabel = composeTestRule.activity.getString(R.string.player_next)
        repeat(10) {
            composeTestRule.onNodeWithText(nextLabel).performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(SWITCH_SETTLE_MILLIS) // let PlayerViewModel's switch debounce (220ms) resolve
        }
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
    }

    private companion object {
        const val SWITCH_SETTLE_MILLIS = 300L
    }
}
