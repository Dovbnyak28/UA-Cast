package com.uacastplayer.player

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Whether the channel you switch to gets its own retry budget, or the exhausted remains of the one
 * you left.
 *
 * [PlayerViewModel] is the largest piece of impure glue in this app and had no unit test at all -
 * only an instrumented lifecycle test, which needs a device. It turns out the whole retry pipeline
 * runs under Robolectric perfectly well as long as the failure is a *network* one: no codec is ever
 * reached, so nothing needs a real decoder.
 *
 * The defect: `switchToIndexImmediate` resets both halves of the per-channel *stall* state and left
 * `retryState` alone. [PlaybackRetryPolicy] counts attempts at playing one channel - `start()`
 * resets it, and playing successfully resets it - so a channel abandoned after burning all four
 * tries handed the next channel a budget already at MAX. That channel's very first network error
 * went straight to GiveUp: marked dead, and with auto-skip on, skipped to another that inherited
 * the same empty budget and died on its first error too. One broken channel could bury a run of
 * working ones - the same cascade [DeadChannelPolicy] exists to prevent, arriving by the one door
 * that check does not cover.
 *
 * Both channels here are permanently broken, which is what makes the measurement possible: each one
 * runs its ladder to the end, and the number of times its origin was contacted is how much budget
 * it was given. The assertion compares the second channel's attempts against the first's rather
 * than against a hardcoded number, because the exact count is ExoPlayer's business (its loader
 * retries each prepare a few times on its own) and not something this test should pin down. The
 * separation is not marginal: a channel given its own budget contacts its origin about five times
 * as often as one given none.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlayerRetryBudgetTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        // Runs onCleared, which releases the ExoPlayer and decrements the process-wide live-instance
        // counter PlayerViewModel guards itself with - without this the next test in this JVM would
        // construct a second one while this is still alive and trip that guard.
        viewModelStore.clear()
        PlaybackActivity.setActive(false)
    }

    /** Answers every request with a 404 and counts how many it was asked. */
    private class BrokenOrigin : AutoCloseable {
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
                                write(NOT_FOUND.toByteArray())
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

    /**
     * The device has to report a *validated* network or [DeadChannelPolicy] refuses to blame the
     * channel at all and the player just waits for connectivity - see `giveUpOnCurrentChannel`.
     * Robolectric's default network reports neither capability.
     */
    private fun giveTheDeviceANetwork() {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(manager).setNetworkCapabilities(network, capabilities)
    }

    private fun player(): PlayerViewModel {
        giveTheDeviceANetwork()
        val provider = ViewModelProvider.create(
            store = viewModelStore,
            factory = ViewModelProvider.AndroidViewModelFactory(application),
            extras = MutableCreationExtras(),
        )
        return provider[PlayerViewModel::class.java]
    }

    /**
     * The retry ladder's delays are coroutines on the main looper, which Robolectric holds paused,
     * while the loads themselves run on ExoPlayer's own real threads. So this drives both: it
     * advances the virtual clock the delays are waiting on, and yields real time for the network to
     * make progress.
     */
    private fun pumpUntil(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + PUMP_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline && !condition()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(VIRTUAL_STEP_MILLIS))
            Thread.sleep(REAL_STEP_MILLIS)
        }
        return condition()
    }

    private fun channel(name: String, url: String) = M3uChannel(displayName = name, streamUrl = url)

    @Test
    fun `a channel switched to is given its own retry budget`() {
        BrokenOrigin().use { firstOrigin ->
            BrokenOrigin().use { secondOrigin ->
                val player = player()
                // Off, so giving up on a channel leaves the player on it instead of walking away -
                // this test is about how much each channel is tried, not about where it lands.
                player.autoSkipDeadEnabled = false
                player.start(
                    listOf(channel("First", firstOrigin.url), channel("Second", secondOrigin.url)),
                    startIndex = 0,
                )

                assertTrue(
                    "the first channel should run out of retries and report a fatal error",
                    pumpUntil { player.uiState.value.fatalError },
                )
                val firstChannelAttempts = firstOrigin.requests.get()
                assertTrue(
                    "the first channel should have been retried, not abandoned at once",
                    firstChannelAttempts > 1,
                )

                player.navigation.requestSwitch(1)
                assertTrue(
                    "the switch should have landed on the second channel",
                    pumpUntil { player.uiState.value.currentChannel?.displayName == "Second" },
                )
                assertTrue(
                    "the second channel should run its own course and report a fatal error too",
                    pumpUntil { player.uiState.value.fatalError },
                )

                val secondChannelAttempts = secondOrigin.requests.get()
                assertTrue(
                    "the second channel got $secondChannelAttempts attempts against the first's " +
                        "$firstChannelAttempts - it inherited a spent retry budget instead of its own",
                    secondChannelAttempts >= firstChannelAttempts / 2,
                )
            }
        }
    }

    private companion object {
        const val NOT_FOUND = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        const val PUMP_TIMEOUT_MILLIS = 30_000L
        const val VIRTUAL_STEP_MILLIS = 50L
        const val REAL_STEP_MILLIS = 10L
    }
}
