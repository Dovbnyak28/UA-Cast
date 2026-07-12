package com.uacastplayer.data.cast

import java.net.Inet4Address
import java.net.NetworkInterface

/** The receiver reaches our proxy over the LAN, so `127.0.0.1` won't do - we need the phone's own IPv4 address. */
object LocalNetworkAddress {

    fun currentIpv4Address(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
