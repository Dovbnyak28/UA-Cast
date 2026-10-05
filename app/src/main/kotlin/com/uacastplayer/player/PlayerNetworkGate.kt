package com.uacastplayer.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.uacastplayer.core.concurrent.runCatchingNonFatal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The player's single definition of usable connectivity: internet capability that Android has
 * actually validated. A captive portal has INTERNET but not VALIDATED and must neither keep a bad
 * channel alive nor wake a retry that will immediately fail again.
 */
internal class PlayerNetworkGate(context: Context) {

    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun hasValidatedNetwork(): Boolean {
        val manager = connectivityManager ?: return false
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        return capabilities.hasValidatedInternet()
    }

    /** Suspends until a validated network is available, or [timeoutMillis] passes. */
    suspend fun awaitValidatedNetworkOrTimeout(timeoutMillis: Long) {
        val manager = connectivityManager ?: return delay(timeoutMillis)
        if (hasValidatedNetwork()) return

        val available = CompletableDeferred<Unit>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available.complete(Unit)
            }
        }
        val registered = runCatchingNonFatal {
            manager.registerNetworkCallback(validatedInternetRequest(), callback)
        }.isSuccess
        try {
            withTimeoutOrNull(timeoutMillis) {
                if (registered) available.await() else awaitCancellation()
            }
        } finally {
            // Network arrival, timeout and ViewModel cancellation all release the process-wide
            // callback. A registration refused by the OS has nothing to unregister.
            if (registered) runCatchingNonFatal { manager.unregisterNetworkCallback(callback) }
        }
    }
}

internal fun NetworkCapabilities?.hasValidatedInternet(): Boolean =
    this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

/** Kept as a visible seam so the request cannot quietly drift from [hasValidatedInternet]. */
internal fun validatedInternetRequest(): NetworkRequest = NetworkRequest.Builder()
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    .build()
