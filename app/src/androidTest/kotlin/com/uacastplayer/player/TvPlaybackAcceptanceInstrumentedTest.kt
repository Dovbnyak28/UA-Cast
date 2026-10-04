@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.uacastplayer.player

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.loadPlaylistFromFile
import com.uacastplayer.testsupport.appViewModelOf
import com.uacastplayer.testsupport.awaitComposeHierarchy
import com.uacastplayer.ui.player.PlayerRequestViewModel
import com.uacastplayer.ui.tv.isTelevision
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit real-TV/network acceptance; refuses to replace existing playlists or change onboarding. */
@RunWith(AndroidJUnit4::class)
class TvPlaybackAcceptanceInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun importedPublicVideoRendersAndTvPlayerSurvivesReplacementThenCloses() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvPlaybackAcceptance") == "true")
        val context = instrumentation.targetContext
        assertTrue("Acceptance must use the isolated debug package", context.packageName.endsWith(".debug"))
        assertTrue("Acceptance requires a real television configuration", context.isTelevision())
        rule.awaitComposeHierarchy(60_000)
        rule.onNodeWithTag("tv_search").assertIsDisplayed()
        assertTrue("Do not replace existing TV sources", appViewModelOf(rule.activity).playlistSources.value.isEmpty())
        assertFalse("Do not replace an existing TV playlist", appViewModelOf(rule.activity).playlistState.value.hasChannels)
        val preferences = AppPreferences(context)
        val previousLastWatched = preferences.lastWatchedChannelKey
        val fixture = File.createTempFile("uacast-tv-acceptance-", ".m3u8", context.cacheDir)
        val frames = AtomicInteger()
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { frames.incrementAndGet() }
            override fun onPlayerError(error: PlaybackException) { report("player-error=${error.errorCode}") }
        }
        try {
            importFixture(fixture)
            onPlayer { it.player.addListener(listener) }
            rule.onNodeWithTag("tv_channel_0").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
            rule.waitUntil(10_000) {
                var opened = false
                rule.activityRule.scenario.onActivity {
                    opened = ViewModelProvider(it)[PlayerRequestViewModel::class.java].request.value != null
                }
                opened
            }
            // The request Flow can publish before Compose mounts PlayerHost. This test's
            // polling below intentionally does not drive Compose's controlled frame clock.
            rule.waitForIdle()
            report("dpad-open=PASS")
            awaitMovingVideo(frames)
            verifyLifecycle()
            verifyRapidReplacement(frames)
            verifyBackClosesPlayback()
        } finally {
            onPlayer { it.player.removeListener(listener); it.releasePlayback() }
            rule.activityRule.scenario.onActivity {
                appViewModelOf(it).playlistController.cancelPendingSourceAdd()
            }
            preferences.lastWatchedChannelKey = previousLastWatched
            assertTrue("Remove only the owned acceptance fixture", !fixture.exists() || fixture.delete())
            assertTrue("Acceptance must leave source storage empty", appViewModelOf(rule.activity)
                .playlistSources.value.isEmpty())
        }
    }

    private fun importFixture(fixture: File) {
        // Public samples from AndroidX Media's demo media.exolist.json; no advertising URLs.
        fixture.writeText("#EXTM3U\n#EXTINF:-1,UA-Cast TV acceptance MKV\n$MKV_URL\n" +
            "#EXTINF:-1,UA-Cast TV acceptance MP4\n$MP4_URL\n")
        rule.activityRule.scenario.onActivity { appViewModelOf(it).loadPlaylistFromFile(Uri.fromFile(fixture)) }
        rule.waitUntil(30_000) {
            val state = appViewModelOf(rule.activity).playlistState.value
            !state.isLoading && state.channels.size == 2
        }
        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty()
        }
        report("import=PASS channels=2")
    }

    private fun awaitMovingVideo(frames: AtomicInteger) {
        var firstPosition: Long? = null
        var advanced = false
        val started = SystemClock.elapsedRealtime()
        while (!advanced && SystemClock.elapsedRealtime() - started < 45_000) {
            onPlayer { owner ->
                val player = owner.player
                assertFalse("Public sample must not enter a fatal player state", owner.uiState.value.fatalError)
                if (player.isPlaying && frames.get() > 0 && player.videoSize.width > 0) {
                    if (firstPosition == null) firstPosition = player.currentPosition
                    advanced = player.currentPosition - checkNotNull(firstPosition) >= 1_000
                }
            }
            if (!advanced) SystemClock.sleep(100)
        }
        if (!advanced) onPlayer {
            report("video-timeout state=${it.player.playbackState} wants=${it.player.playWhenReady} " +
                "playing=${it.player.isPlaying} frames=${frames.get()} items=${it.player.mediaItemCount} " +
                "width=${it.player.videoSize.width} error=${it.player.playerError?.errorCode}")
        }
        assertTrue("TV must render a frame and advance video, not merely reach READY", advanced)
        onPlayer {
            assertTrue("Video must have decoded height", it.player.videoSize.height > 0)
            report("video=PASS width=${it.player.videoSize.width} height=${it.player.videoSize.height}")
        }
    }

    private fun verifyLifecycle() {
        lateinit var original: PlayerViewModel
        onPlayer { original = it; it.togglePlayback() }
        onPlayer { assertFalse("Pause must clear playback intent", it.player.playWhenReady); it.togglePlayback() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        onPlayer { assertFalse("Background TV playback must pause", it.player.playWhenReady) }
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        onPlayer { assertTrue("Foreground must resume the previously playing stream", it.player.playWhenReady) }
        rule.activityRule.scenario.recreate()
        rule.awaitComposeHierarchy(60_000)
        onPlayer { assertTrue("Recreation must reuse the player owner", original === it) }
        assertEquals("No second ExoPlayer owner", 1, PlayerViewModel.liveInstanceCountForTest())
        report("pause-resume=PASS background-foreground=PASS recreation=PASS owners=1")
    }

    private fun verifyRapidReplacement(frames: AtomicInteger) {
        val channels = appViewModelOf(rule.activity).playlistState.value.channels
        assertEquals(2, channels.size)
        frames.set(0)
        onPlayer { owner ->
            repeat(30) { index -> owner.start(channels, index % channels.size) }
            assertTrue("The last channel switch must win", owner.uiState.value.currentChannel == channels[1])
        }
        assertEquals("Rapid replacement must reuse one player", 1, PlayerViewModel.liveInstanceCountForTest())
        awaitMovingVideo(frames)
        report("switches30=PASS last-channel=PASS owners=1")
    }

    private fun verifyBackClosesPlayback() {
        // The first Back may only hide TV controls. The second must close, never create a mini-player.
        repeat(2) {
            rule.activityRule.scenario.onActivity { activity ->
                if (ViewModelProvider(activity)[PlayerRequestViewModel::class.java].request.value != null) {
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            }
            rule.waitForIdle()
        }
        rule.onNodeWithTag("tv_search").assertIsDisplayed()
        onPlayer {
            assertFalse("Closing TV player must stop audio/video", it.player.isPlaying)
            assertEquals("Closing TV player must clear its decoder workload", Player.STATE_IDLE, it.player.playbackState)
            assertEquals("Closing TV player must clear the stream", 0, it.player.mediaItemCount)
        }
        report("back-exit=PASS media-items=0 idle=PASS")
    }

    private fun onPlayer(action: (PlayerViewModel) -> Unit) {
        rule.activityRule.scenario.onActivity { action(ViewModelProvider(it)[PlayerViewModel::class.java]) }
    }

    private fun report(message: String) {
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "TV_ACCEPTANCE $message\n") })
    }

    private companion object {
        const val MKV_URL = "https://storage.googleapis.com/exoplayer-test-media-1/mkv/" +
            "android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv"
        const val MP4_URL = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4"
    }
}
