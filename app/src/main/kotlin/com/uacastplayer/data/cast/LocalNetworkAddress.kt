package com.uacastplayer.data.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/**
 * The receiver reaches our proxy over the LAN, so `127.0.0.1` won't do - we need the phone's own
 * IPv4 address on its Wi-Fi network specifically, not just any up interface. A phone routinely has
 * several up, non-loopback interfaces at once (mobile data alongside Wi-Fi is common - confirmed
 * in the field: `rmnet_data2`/10.x up next to `wlan0`/192.168.x), and enumerating interfaces
 * without distinguishing which one is actually Wi-Fi risks handing the Chromecast an address on a
 * network it can't reach at all. That failure is silent from here: the receiver's HTTP fetch to
 * the unreachable proxy URL just times out, surfacing on the sender only as a generic idle/error
 * status indistinguishable from a real codec or content problem.
 */
object LocalNetworkAddress {

    // allNetworks has no synchronous, callback-free replacement - this is a one-shot lookup at
    // proxy-start time, not something worth the async NetworkCallback registration API for.
    @Suppress("DEPRECATION")
    fun currentIpv4Address(context: Context): String? {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return connectivityManager?.allNetworks
            ?.firstOrNull { isWifi(connectivityManager, it) }
            ?.let(connectivityManager::getLinkProperties)
            ?.linkAddresses
            ?.mapNotNull { it.address as? Inet4Address }
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    private fun isWifi(connectivityManager: ConnectivityManager, network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
