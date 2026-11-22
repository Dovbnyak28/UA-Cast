package app.zorik.player.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import app.zorik.player.core.concurrent.runCatchingNonFatal
import app.zorik.player.log.AppLog

// Same tag the repository logs under: these lines used to live there.
private const val TAG = "DlnaSessionRepository"

/** The callback must observe the same Wi-Fi network that supplies the proxy address, even when
 * it is local-only and has no INTERNET capability. No cellular/VPN event can change that address. */
internal fun dlnaSessionNetworkRequest(): NetworkRequest = NetworkRequest.Builder().apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        clearCapabilities()
    } else {
        // clearCapabilities was added in API 30. Remove the builder's default filters on older
        // devices so a local-only/restricted Wi-Fi does not disappear from this callback.
        removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
        removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }
    addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
}.build()

/** Registers the session's Wi-Fi callback; owns no state - the caller keeps the returned handle. */
internal object DlnaNetworkWatch {

    /**
     * Calls [onChange] on every availability, loss, capability or link change of the network
     * [dlnaSessionNetworkRequest] matches. Returns the handle that unregisters the callback, or
     * null when there is no ConnectivityManager or registration failed (already logged).
     */
    fun register(context: Context, onChange: () -> Unit): AutoCloseable? {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChange()
            override fun onLost(network: Network) = onChange()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                onChange()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                onChange()
        }
        val request = dlnaSessionNetworkRequest()
        return runCatchingNonFatal { connectivityManager.registerNetworkCallback(request, callback) }
            .map {
                AutoCloseable {
                    runCatchingNonFatal { connectivityManager.unregisterNetworkCallback(callback) }
                        .onFailure { e ->
                            AppLog.w(TAG) { "Cannot stop the DLNA network watch: ${e.javaClass.simpleName}" }
                        }
                }
            }
            .onFailure { e -> AppLog.w(TAG) { "Cannot watch the network for this session: ${e.javaClass.simpleName}" } }
            .getOrNull()
    }
}
