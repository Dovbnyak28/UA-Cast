package com.uacastplayer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** What the active network is, read straight from [ConnectivityManager]; holds no state. */
internal object AppNetworkInfo {
    /**
     * Whether the active network is one the app may spend a guide download on - see
     * [com.uacastplayer.epg.EpgRefreshPolicy]. Unknown counts as metered: guessing wrong in the
     * other direction spends somebody's mobile data on 50MB they did not ask for.
     */
    fun isUnmetered(context: Context): Boolean {
        val manager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = manager?.activeNetwork?.let(manager::getNetworkCapabilities)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }

    /**
     * Wi-Fi, mobile or nothing, and whether the system calls it metered.
     *
     * Buffering reports are unanswerable without it and it needs no permission beyond the
     * ACCESS_NETWORK_STATE this app already holds. Deliberately only the *kind* of network - no
     * SSID, no carrier, no address, none of which would help and all of which identify a place.
     */
    fun describe(context: Context): String {
        val manager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = manager?.activeNetwork?.let(manager::getNetworkCapabilities)
            ?: return if (manager == null) "?" else "none"
        val kind = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "$kind${if (metered) ", metered" else ""}${if (validated) "" else ", not validated"}"
    }
}
