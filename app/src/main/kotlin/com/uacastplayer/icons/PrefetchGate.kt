package com.uacastplayer.icons

/** Whether the background full-icon-pack prefetch may run right now. */
object PrefetchGate {
    fun canPrefetchNow(wifiOnlyEnabled: Boolean, isConnected: Boolean, isMetered: Boolean): Boolean {
        if (!isConnected) return false
        if (wifiOnlyEnabled && isMetered) return false
        return true
    }
}
