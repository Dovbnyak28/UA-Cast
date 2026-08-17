package com.uacastplayer.data.icons

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.M3uChannel
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * The progress counter shown while icons prefetch, under real concurrency.
 *
 * [IconPrefetcher.prefetch] fans a channel list out across up to six concurrent coroutines (see
 * [MAX_CONCURRENT_FETCHES]) and reports progress as each one finishes. That report is read by
 * `IconController`, which publishes it to a StateFlow the settings screen renders as "N / total".
 *
 * `completed` was a plain `var Int`, incremented outside the semaphore that limits *fetches* -
 * nothing serialized the increment itself, so up to six coroutines could read the same value,
 * each add one, and each report the same (too-low) result. `i++` is read-modify-write, not atomic,
 * and coroutines launched without a fixed dispatcher run on whatever threads the underlying pool
 * hands out - genuinely in parallel on a multi-core JVM, which is exactly what a real phone is.
 *
 * The visible failure is a progress readout that can undercount, stall short of `total`, or
 * plateau and jump rather than counting up one at a time - cosmetic, but on the one number this
 * feature has to get right, since it is the only feedback a user watching a long prefetch gets.
 *
 * This test forces real OS-thread parallelism (a fixed thread pool, not `Dispatchers.Unconfined`
 * or a single-threaded one - either would serialize the coroutines by accident and prove nothing)
 * and enough channels that the race window is crossed many times per run. A race is inherently
 * probabilistic to observe from outside; the case against the old code is the static one stated
 * above - non-atomic shared mutable state, mutated from multiple real threads, with nothing
 * ordering the two - and this test corroborates it rather than being the only evidence for it.
 * Every channel resolves with no candidates at all (no tvg-logo, no tvg-id), so the real
 * [IconRepository] does no disk or network I/O and the loop runs fast enough to fit many channels
 * into one test.
 */
@RunWith(RobolectricTestRunner::class)
class IconPrefetcherProgressTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    /** [IconPrefetcher.isConnected] reads a real network, which Robolectric otherwise reports as
     * absent - without this the gate in [IconPrefetcher.prefetch] refuses to run at all, and the
     * test would fail on "nothing was ever reported" rather than on the race it exists to catch. */
    @Before
    fun fakeAnActiveNetwork() {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(manager).setDefaultNetworkActive(true)
        val network = manager.activeNetwork ?: return
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(network, capabilities)
    }

    private fun channel(index: Int) = M3uChannel(
        displayName = "Channel $index",
        streamUrl = "http://example.test/$index.m3u8",
        tvgId = null,
        tvgName = null,
        tvgLogo = null,
        groupTitle = null,
        userAgent = null,
        referrer = null,
    )

    @Test
    fun `the reported progress reaches exactly total, every run`() {
        val prefetcher = IconPrefetcher(application, IconRepository(application))
        val channels = (1..CHANNEL_COUNT).map(::channel)
        val threadPool = Executors.newFixedThreadPool(THREAD_COUNT)
        try {
            repeat(RUNS) { run ->
                var lastReported = -1
                var maxReported = -1
                runBlocking(threadPool.asCoroutineDispatcher()) {
                    prefetcher.prefetch(channels, wifiOnly = false) { progress ->
                        lastReported = progress.completed
                        maxReported = maxOf(maxReported, progress.completed)
                    }
                }
                assertEquals(
                    "run $run: the last progress report must say every channel finished",
                    CHANNEL_COUNT,
                    lastReported,
                )
                assertEquals(
                    "run $run: a lost increment means the count never actually reached total",
                    CHANNEL_COUNT,
                    maxReported,
                )
            }
        } finally {
            threadPool.shutdown()
        }
    }

    private companion object {
        const val CHANNEL_COUNT = 4000
        const val THREAD_COUNT = 6
        const val RUNS = 5
    }
}
