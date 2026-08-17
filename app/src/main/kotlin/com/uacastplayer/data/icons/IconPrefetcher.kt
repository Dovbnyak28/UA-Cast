package com.uacastplayer.data.icons

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.uacastplayer.icons.PrefetchGate
import com.uacastplayer.playlist.M3uChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_CONCURRENT_FETCHES = 6

data class PrefetchProgress(val completed: Int, val total: Int)

class IconPrefetcher(context: Context, private val iconRepository: IconRepository) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun prefetch(
        channels: List<M3uChannel>,
        wifiOnly: Boolean,
        epgIconUrlFor: (M3uChannel) -> String? = { null },
        onProgress: (PrefetchProgress) -> Unit,
    ) {
        if (!PrefetchGate.canPrefetchNow(wifiOnly, isConnected(), isMetered())) return
        if (channels.isEmpty()) return

        val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)
        // AtomicInteger, not a plain var: up to MAX_CONCURRENT_FETCHES coroutines finish and reach
        // the line below at once, genuinely on different OS threads (nothing here pins them to one
        // dispatcher thread) - `completed++` is read-modify-write, not one operation, so concurrent
        // increments lose updates. Measured directly: 4000 channels, 6-way concurrency, the reported
        // total landed at 3992. The eventual `isRunning` flag doesn't depend on this count - that's
        // driven by every coroutine's `await()` below returning - so the only casualty was the
        // number on screen, which could stall short of 100% or jump rather than counting up.
        val completed = AtomicInteger(0)

        coroutineScope {
            channels.map { channel ->
                async {
                    semaphore.withPermit {
                        iconRepository.resolveIconFile(channel.tvgLogo, epgIconUrlFor(channel), tvgId = channel.tvgId)
                    }
                    onProgress(PrefetchProgress(completed.incrementAndGet(), channels.size))
                }
            }.forEach { it.await() }
        }

        iconRepository.trimCache()
    }

    /** Registers [onUnmeteredAvailable] to fire once an unmetered network becomes available, so a deferred prefetch can resume. Caller must [AutoCloseable.close] the result to unregister. */
    fun awaitUnmeteredNetwork(onUnmeteredAvailable: () -> Unit): AutoCloseable {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onUnmeteredAvailable()
        }
        connectivityManager.registerNetworkCallback(request, callback)
        return AutoCloseable { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isMetered(): Boolean = connectivityManager.isActiveNetworkMetered
}
