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
 * What a scheduled stall recovery does when playback is no longer wanted by the time it fires.
 *
 * A silent stall schedules `performStallRecovery` behind a backoff that reaches thirty seconds in
 * [StallRetryPolicy]'s steady state, and every third attempt is the HEAVY one, which ends in
 * `play()`. Nothing re-checked, after that wait, whether playing was still the right thing to do.
 *
 * So: watch a stalling stream, press pause or Home, and up to thirty seconds later the app started
 * playing again on its own. Backgrounding is the worse half - `onEnterBackground` pauses precisely
 * so a live stream does not keep downloading with the screen off, and the pending recovery undid
 * that, leaving the stream playing in the background indefinitely (once playing, the sampler keeps
 * finding it healthy and never intervenes again).
 *
 * `sampleForStall` already refuses to act on a stall while `playWhenReady` is false or while a
 * receiver has the stream - the rule was established, it just was not re-applied on the far side of
 * the delay. These tests drive the two reachable halves of it through the real entry points, and
 * the third asserts the guard does not block a recovery that *is* wanted.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlayerStallRecoveryTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        // Runs onCleared, releasing the ExoPlayer and the process-wide live-instance count that
        // PlayerViewModel guards itself with.
        viewModelStore.clear()
        PlaybackActivity.setActive(false)
    }

    /** Accepts requests, counts them, and answers each with an empty 200 - enough for a prepare to
     * be observable without any of it having to be playable. */
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
        assertTrue("the player should be trying to play before anything stops it", player.player.playWhenReady)
        return player
    }

    /**
     * The worse half. The app pauses itself when it leaves the screen; a recovery that fires
     * afterwards must not undo that, or a live stream goes on downloading with the screen off.
     */
    @Test
    fun `a recovery that fires after the app is backgrounded does not resume playback`() {
        CountingOrigin().use { origin ->
            val player = playerWatching(origin)

            player.onEnterBackground(isInPictureInPicture = false)
            settle()
            assertFalse("onEnterBackground should have paused the player", player.player.playWhenReady)

            // The HEAVY recovery - the one that ends in play(). Attempt 3 is heavy; see
            // StallRetryPolicy.recoveryKindFor.
            player.performStallRecovery(attempt = 3)
            settle()

            assertFalse(
                "a stall recovery must not resume a stream the app deliberately backgrounded",
                player.player.playWhenReady,
            )
        }
    }

    /** The half a user sees directly: they paused it, and it started itself again. */
    @Test
    fun `a recovery that fires after the user pauses does not resume playback`() {
        CountingOrigin().use { origin ->
            val player = playerWatching(origin)

            player.player.pause()
            settle()
            assertFalse("the player should be paused", player.player.playWhenReady)

            player.performStallRecovery(attempt = 3)
            settle()

            assertFalse(
                "a stall recovery must not resume a stream the user paused",
                player.player.playWhenReady,
            )
        }
    }

    /**
     * The control, and the reason the two above mean "refused" rather than "does nothing anyway":
     * with playback genuinely wanted, the same call does reach the origin.
     */
    @Test
    fun `a recovery still runs when playback is wanted`() {
        CountingOrigin().use { origin ->
            val player = playerWatching(origin)
            val before = origin.requests.get()

            player.performStallRecovery(attempt = 3)

            val deadline = System.currentTimeMillis() + REQUEST_WAIT_MILLIS
            while (System.currentTimeMillis() < deadline && origin.requests.get() <= before) {
                settle()
                Thread.sleep(REAL_STEP_MILLIS)
            }
            assertTrue(
                "a wanted recovery must re-prepare the stream, not be skipped along with the rest",
                origin.requests.get() > before,
            )
            assertTrue("and it must leave the player trying to play", player.player.playWhenReady)
        }
    }

    private companion object {
        const val EMPTY_OK = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        const val SETTLE_MILLIS = 200L
        const val REQUEST_WAIT_MILLIS = 10_000L
        const val REAL_STEP_MILLIS = 10L
    }
}
