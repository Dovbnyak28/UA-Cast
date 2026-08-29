package com.uacastplayer.data.icons

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.icons.IconMemoryCacheKey
import com.uacastplayer.icons.PrefetchGate
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.M3uChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_CONCURRENT_FETCHES = 6
private const val TAG = "IconPrefetcher"

data class PrefetchProgress(val completed: Int, val total: Int)

internal data class IconPrefetchWork(
    val channel: M3uChannel,
    val epgIconUrl: String?,
    /** Number of input channels represented by this unique resolution key. */
    val progressWeight: Int,
)

/** Builds the bounded worker queue up front and coalesces channels with the same candidate chain. */
internal fun iconPrefetchWork(
    channels: List<M3uChannel>,
    epgIconUrlFor: (M3uChannel) -> String?,
): List<IconPrefetchWork> {
    val unique = linkedMapOf<String, IconPrefetchWork>()
    for (channel in channels) {
        val epgIconUrl = epgIconUrlFor(channel)
        val key = IconMemoryCacheKey.of(channel.tvgLogo, epgIconUrl, channel.tvgId)
        val previous = unique[key]
        unique[key] = if (previous == null) {
            IconPrefetchWork(channel, epgIconUrl, progressWeight = 1)
        } else {
            previous.copy(progressWeight = previous.progressWeight + 1)
        }
    }
    return unique.values.toList()
}

class IconPrefetcher(context: Context, private val iconRepository: IconRepository) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun prefetch(
        channels: List<M3uChannel>,
        wifiOnly: Boolean,
        epgIconUrlFor: (M3uChannel) -> String? = { null },
        onProgress: (PrefetchProgress) -> Unit,
    ): Boolean {
        if (channels.isEmpty() || !PrefetchGate.canPrefetchNow(wifiOnly, isConnected(), isMetered())) return false

        val work = iconPrefetchWork(channels, epgIconUrlFor)
        // The callback must be serialized with the increment, not merely fed an atomic number.
        // Otherwise coroutine A can increment to 1, pause, coroutine B report 2, then A report 1:
        // a StateFlow consumer visibly moves backwards and the final callback need not be `total`.
        val progressMutex = Mutex()
        var completed = 0

        coroutineScope {
            val queue = Channel<IconPrefetchWork>(capacity = MAX_CONCURRENT_FETCHES)
            val producer = launch {
                for (item in work) queue.send(item)
                queue.close()
            }
            val workers = List(minOf(MAX_CONCURRENT_FETCHES, work.size)) {
                launch {
                    for (item in queue) {
                        iconRepository.resolveIconFile(
                            item.channel.tvgLogo,
                            item.epgIconUrl,
                            tvgId = item.channel.tvgId,
                        )
                        progressMutex.withLock {
                            completed += item.progressWeight
                            onProgress(PrefetchProgress(completed, channels.size))
                        }
                    }
                }
            }
            producer.join()
            workers.forEach { worker ->
                worker.join()
            }
        }

        iconRepository.trimCache()
        return true
    }

    /** Resumes a deferred prefetch when an internet-capable network becomes available. The caller
     * must close the result to unregister the callback. Metered eligibility stays in
     * [PrefetchGate], so a mobile network can resume a prefetch when the user has allowed it while
     * a metered callback remains harmless when Wi-Fi-only mode is enabled. */
    fun awaitNetwork(onNetworkAvailable: () -> Unit): AutoCloseable {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onNetworkAvailable()
        }
        val registered = runCatchingNonFatal {
            connectivityManager.registerNetworkCallback(request, callback)
        }.onFailure { error ->
            AppLog.w(TAG) { "Cannot watch for an internet-capable network: ${error.javaClass.simpleName}" }
        }.isSuccess
        return AutoCloseable {
            if (registered) {
                runCatchingNonFatal { connectivityManager.unregisterNetworkCallback(callback) }
                    .onFailure { error ->
                        AppLog.w(TAG) { "Cannot stop the network watch: ${error.javaClass.simpleName}" }
                    }
            }
        }
    }

    private fun isConnected(): Boolean {
        val capabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun isMetered(): Boolean = connectivityManager.isActiveNetworkMetered
}
