package com.uacastplayer.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

object RemoteLanAddress {
    /** TVs are often wired while phones use Wi-Fi. VPN/cellular addresses cannot reach the local receiver. */
    @Suppress("DEPRECATION")
    fun find(context: Context): String? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        return manager.allNetworks.firstNotNullOfOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            val lan = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!lan || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@firstNotNullOfOrNull null
            manager.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull { link ->
                (link.address as? Inet4Address)?.takeUnless { it.isLoopbackAddress || it.isLinkLocalAddress }
                    ?.hostAddress?.takeIf(RemoteEndpoint::isLocalIpv4)
            }
        }
    }
}
