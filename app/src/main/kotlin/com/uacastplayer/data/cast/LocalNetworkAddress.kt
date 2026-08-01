package com.uacastplayer.data.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.uacastplayer.log.AppLog
import java.net.Inet4Address

private const val TAG = "LocalNetworkAddress"

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
        val address = connectivityManager?.allNetworks
            ?.firstOrNull { isReachableWifi(connectivityManager, it) }
            ?.let(connectivityManager::getLinkProperties)
            ?.linkAddresses
            ?.mapNotNull { it.address as? Inet4Address }
            // Link-local (169.254.x.x) means DHCP failed - an address the receiver can't route
            // to any better than loopback, so handing it out would just fail more slowly.
            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
        // The single most useful line when a cast fails with the receiver never fetching anything:
        // it says whether the address handed out is the one the receiver shares a LAN with. A
        // private LAN address is not sensitive - it identifies nothing about the user - and without
        // it a wrong-interface failure is indistinguishable from a codec or content problem.
        AppLog.d(TAG) { "Local LAN address for the proxy: ${address ?: "none found"}" }
        return address
    }

    /**
     * Wi-Fi **and not a VPN**. A VPN's [NetworkCapabilities] inherit the transports of the networks
     * it runs over, so a VPN tunnelled over Wi-Fi reports `TRANSPORT_WIFI` exactly like the real
     * Wi-Fi network does. `allNetworks` has no defined order, so a plain Wi-Fi check picked
     * whichever came first and could hand the Chromecast the VPN's tunnel address - which is on the
     * VPN's private range, not the LAN, and which the receiver cannot route to at all.
     *
     * That is the failure this class's own doc already describes as silent: the receiver accepts
     * the load, its fetch to an unreachable url goes nowhere, and the sender sees only a generic
     * idle/error. Field reports of casting being unusable specifically while a VPN is connected
     * match it exactly.
     */
    private fun isReachableWifi(connectivityManager: ConnectivityManager, network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
