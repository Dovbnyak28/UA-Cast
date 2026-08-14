package com.uacastplayer.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.data.icons.IconPrefetcher
import com.uacastplayer.data.icons.IconRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.player.PlaybackActivity
import com.uacastplayer.playlist.M3uChannel
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * What the icon prefetch's progress indicator says once playback interrupts the prefetch.
 *
 * [IconController] holds the background logo prefetch off while something is playing, and it did
 * that by cancelling the job and nothing else. But the line that clears
 * [com.uacastplayer.icons.IconPrefetchUiState.isRunning] is the *last* line of `runPrefetch`, which
 * a cancellation never reaches - so the flag stayed set with no prefetch behind it. Two places draw
 * from it: the top-bar `DownloadStatusBanner` and the channel list's `LinearProgressIndicator`.
 *
 * Local playback covers both, so the frozen bar was invisible there. Casting does not - the user
 * stays on the channel list for the whole session, watching a progress bar that stopped moving the
 * moment they hit cast. `cancelPrefetch()` already existed and already did the right thing; the
 * collector simply was not using it.
 *
 * The server here holds every icon request open, which is what keeps the prefetch genuinely in
 * flight at the moment the test interrupts it. Waiting on [HeldIconServer.requestReceived] before
 * that is what stops this passing vacuously: if the prefetch had been gated off (no network in the
 * Robolectric environment, say) it would never reach the server and the test fails there rather
 * than "proving" a flag that was never set in the first place.
 */
@RunWith(RobolectricTestRunner::class)
class IconPrefetchInterruptionTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        // Process-wide singleton - leaving it set would follow this test into every other one.
        PlaybackActivity.setActive(false)
        scope.cancel()
    }

    /** Accepts icon requests forever and answers none of them, so every fetch stays in flight. */
    private class HeldIconServer : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker = Executors.newSingleThreadExecutor()

        /** Counts down once some icon request has actually arrived. */
        val requestReceived = CountDownLatch(1)

        val url: String get() = "http://127.0.0.1:${socket.localPort}/logo.png"

        init {
            worker.submit {
                try {
                    while (true) {
                        val client = socket.accept()
                        client.getInputStream().read()
                        requestReceived.countDown()
                        // Deliberately never answered and never closed: the client stays parked in
                        // its read until this server is closed, which is the whole point.
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
     * Robolectric's default active network reports no NET_CAPABILITY_INTERNET, so
     * [com.uacastplayer.icons.PrefetchGate] refuses before a single icon is fetched. That is the
     * environment's default, not the app's behaviour, and it has to be said out loud here or the
     * prefetch never starts and there is nothing to interrupt.
     */
    private fun giveTheDeviceANetwork() {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(network, capabilities)
    }

    private fun controller(): IconController {
        giveTheDeviceANetwork()
        val preferences = AppPreferences(application)
        // Metered is what the Robolectric default network is, so the prefetch has to be allowed to
        // run on one - the gate this test needs open is connectivity, not the wifi-only setting.
        preferences.iconWifiOnly = false
        val iconRepository = IconRepository(application)
        return IconController(
            preferences = preferences,
            iconRepository = iconRepository,
            iconPrefetcher = IconPrefetcher(application, iconRepository),
            scope = scope,
            onPrefetchFinished = {},
        )
    }

    private fun channelsPointingAt(logoUrl: String) = listOf(
        M3uChannel(displayName = "One", streamUrl = "http://example.test/one.ts", tvgLogo = logoUrl),
    )

    private fun IconController.awaitRunning(running: Boolean): Boolean {
        val deadline = System.currentTimeMillis() + STATE_WAIT_MILLIS
        while (System.currentTimeMillis() < deadline && iconPrefetchState.value.isRunning != running) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return iconPrefetchState.value.isRunning == running
    }

    @Test
    fun `a prefetch interrupted by playback stops claiming to be running`() {
        HeldIconServer().use { server ->
            val controller = controller()
            val channels = channelsPointingAt(server.url)
            controller.triggerPrefetch(
                channels = channels,
                iconDisplayMode = IconDisplayMode.CACHE,
                // PrefetchSelectionPolicy only ever picks priority channels - favorites, the
                // last-watched one, the first group - so a bare channel list selects nothing and
                // the prefetch returns before it starts.
                context = IconController.PrefetchContext(firstGroupChannels = channels),
            )

            assertTrue(
                "the prefetch should have got as far as fetching an icon",
                server.requestReceived.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertTrue("the prefetch should be reporting itself as running", controller.awaitRunning(true))

            PlaybackActivity.setActive(true)

            assertFalse(
                "nothing is prefetching once playback has cancelled it, and the progress bars must agree",
                controller.iconPrefetchState.value.isRunning,
            )
        }
    }

    /**
     * The re-arm still works. The controller restarts the prefetch once playback stops, and this
     * pins that clearing the flag on the way in does not cost that: the indicator comes back
     * because the work came back.
     */
    @Test
    fun `the prefetch comes back once playback stops`() {
        HeldIconServer().use { server ->
            val controller = controller()
            val channels = channelsPointingAt(server.url)
            controller.triggerPrefetch(
                channels = channels,
                iconDisplayMode = IconDisplayMode.CACHE,
                // PrefetchSelectionPolicy only ever picks priority channels - favorites, the
                // last-watched one, the first group - so a bare channel list selects nothing and
                // the prefetch returns before it starts.
                context = IconController.PrefetchContext(firstGroupChannels = channels),
            )
            assertTrue(
                "the prefetch should have got as far as fetching an icon",
                server.requestReceived.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertTrue(controller.awaitRunning(true))

            PlaybackActivity.setActive(true)
            assertFalse(controller.iconPrefetchState.value.isRunning)

            PlaybackActivity.setActive(false)

            assertTrue("the prefetch must resume once playback is over", controller.awaitRunning(true))
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 10L
        const val STATE_WAIT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 20L
    }
}
