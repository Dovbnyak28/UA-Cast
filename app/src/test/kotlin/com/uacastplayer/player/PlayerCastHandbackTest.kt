package com.uacastplayer.player

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.M3uChannel
import java.io.IOException
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What happens to the phone when a receiver hands the stream back and nobody is looking.
 *
 * Casting and then putting the phone away is, in [BackgroundPlaybackPolicy]'s own words, "the
 * normal way to use a cast" - so that policy deliberately does not pause on backgrounding while a
 * cast is running. Which means that when the TV is switched off, the app is off screen with nothing
 * paused, and both cast paths answered the disconnect by calling `prepare()` and `play()` on the
 * spot.
 *
 * The result is precisely what [BackgroundPlaybackPolicy] exists to prevent, reached by a door it
 * never sees: a live stream out of a stopped activity, a wake lock and a Wi-Fi lock held, and audio
 * out of the speaker of a phone in somebody's pocket - with no notification to stop it, because
 * this app has no MediaSessionService.
 *
 * The fix defers rather than drops, and both halves matter, so both are pinned here. Dropping the
 * resume would leave the user returning to a dead player: while casting, `switchToIndexImmediate`
 * skips `prepare()` for the current channel, so the media item is set but was never prepared.
 *
 * Note the deliberate `pause()` before backgrounding in the first two tests. It stands in for what
 * a cast leaves behind - a local player that is not trying to play - and it is also what keeps the
 * tests honest: with it, `BackgroundPlaybackPolicy.shouldResumeOnStart` is false, so the only thing
 * that can start playback on return is the deferred resume itself, and the second test cannot pass
 * on the ordinary backgrounding path by accident.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlayerCastHandbackTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        viewModelStore.clear()
        PlaybackActivity.setActive(false)
    }

    /** Answers every request with an empty 200 and counts them. */
    private class CountingOrigin : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newSingleThreadExecutor()

        val requests = AtomicInteger()

        val url: String get() = "http://127.0.0.1:${socket.localPort}/stream.ts"

        init {
            worker.submit {
                try {
                    while (true) {
                        socket.accept().use { client ->
                            val reader = client.getInputStream().bufferedReader()
                            var line = reader.readLine()
                            while (!line.isNullOrEmpty()) {
                                line = reader.readLine()
                            }
                            requests.incrementAndGet()
                            client.getOutputStream().apply {
                                write(EMPTY_OK.toByteArray())
                                flush()
                            }
                        }
                    }
                } catch (_: IOException) {
                    // The socket being closed by close() below is how this thread ends.
                }
            }
        }

        override fun close() {
            worker.shutdownNow()
            socket.close()
        }
    }

    private fun player(): PlayerViewModel {
        val provider = ViewModelProvider.create(
            store = viewModelStore,
            factory = ViewModelProvider.AndroidViewModelFactory(application),
            extras = MutableCreationExtras(),
        )
        return provider[PlayerViewModel::class.java]
    }

    /** Lets the main looper - which Robolectric holds paused - run whatever the player posted to it. */
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SETTLE_MILLIS))
    }

    private fun playerWatching(origin: CountingOrigin): PlayerViewModel {
        val player = player()
        player.start(listOf(M3uChannel(displayName = "Live", streamUrl = origin.url)), startIndex = 0)
        settle()
        return player
    }

    /** A player put away mid-cast: not playing locally, and off screen. */
    private fun PlayerViewModel.putAwayMidCast() {
        player.pause()
        settle()
        onEnterBackground(isInPictureInPicture = false)
        settle()
        assertFalse("the local player should not be trying to play at this point", player.playWhenReady)
    }

    @Test
    fun `a cast handed back while the app is off screen does not start playing`() {
        CountingOrigin().use { origin ->
            val player = playerWatching(origin)
            player.putAwayMidCast()

            player.resumeLocalPlayback()
            settle()

            assertFalse(
                "the phone must not start playing a live stream while it is in somebody's pocket",
                player.player.playWhenReady,
            )
        }
    }

    @Test
    fun `the handed-back stream is picked up once the app is back on screen`() {
        CountingOrigin().use { origin ->
            val player = playerWatching(origin)
            player.putAwayMidCast()
            player.resumeLocalPlayback()
            settle()

            player.onReturnToForeground()
            settle()

            assertTrue(
                "a deferred resume must be paid on return, not dropped - the player would be dead",
                player.player.playWhenReady,
            )
        }
    }

    /**
     * The control: with the app on screen the hand-back is immediate, so the two refusals above
     * mean "deferred", not "this call never does anything".
     */
    @Test
    fun `a cast handed back while the app is on screen resumes at once`() {
        CountingOrigin().use { origin ->
            val player = playerWatching(origin)
            player.player.pause()
            settle()
            assertFalse(player.player.playWhenReady)

            player.resumeLocalPlayback()
            settle()

            assertTrue(
                "with the user watching, the stream must come straight back to the phone",
                player.player.playWhenReady,
            )
        }
    }

    /**
     * Picture-in-picture is a window the user is looking at, so it is not "off screen" - the same
     * distinction [BackgroundPlaybackPolicy.shouldPauseOnStop] already draws for pausing.
     */
    @Test
    fun `picture-in-picture does not count as being off screen`() {
        CountingOrigin().use { origin ->
            val player = playerWatching(origin)
            player.player.pause()
            settle()
            player.onEnterBackground(isInPictureInPicture = true)
            settle()

            player.resumeLocalPlayback()
            settle()

            assertTrue(
                "a hand-back must reach a picture-in-picture window the user is watching",
                player.player.playWhenReady,
            )
        }
    }

    /** Regression for the production wiring, not the pure policy: lifecycle used to pass only the
     * Chromecast flag into [BackgroundPlaybackPolicy], so DLNA was treated as local playback. */
    @Test
    fun `backgrounding does not mutate local playback intent under an active dlna session`() {
        CountingOrigin().use { origin ->
            val player = playerWatching(origin)
            // This is the state handleDlnaStateChange creates: the local player is stopped without
            // clearing playWhenReady, while the renderer owns the stream.
            player.player.stop()
            player.setRemoteCastingForLifecycleTest(chromecast = false, dlna = true)
            assertTrue(player.player.playWhenReady)

            player.onEnterBackground(isInPictureInPicture = false)
            settle()

            assertTrue(
                "DLNA ownership must be treated like Chromecast, not paused as local playback",
                player.player.playWhenReady,
            )
            player.onReturnToForeground()
            settle()
            assertTrue(player.player.playWhenReady)
        }
    }

    @Test
    fun `closing local player keeps prefetch gated while a receiver is active`() {
        val player = player()
        player.setRemoteCastingForLifecycleTest(chromecast = true, dlna = false)
        PlaybackActivity.setActive(true)

        player.releasePlayback()

        assertTrue(
            "closing PlayerHost must not advertise an active receiver as idle",
            PlaybackActivity.isActive.value,
        )
        assertTrue(
            "reopening the player must still render its Chromecast state",
            player.uiState.value.isCasting,
        )
    }

    private companion object {
        const val EMPTY_OK = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        const val SETTLE_MILLIS = 200L
    }
}
