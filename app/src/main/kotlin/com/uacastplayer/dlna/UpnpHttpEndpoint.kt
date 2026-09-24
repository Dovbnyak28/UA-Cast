package com.uacastplayer.dlna

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

/** Normalizes untrusted SSDP/UPnP endpoint values before they reach OkHttp. */
internal object UpnpHttpEndpoint {

    fun absolute(value: String): String? = parse(value)?.takeIf(::isSupported)?.toString()

    fun resolve(base: String, reference: String): String? = try {
        URI(base).resolve(URI(reference)).takeIf(::isSupported)?.toString()
    } catch (_: URISyntaxException) {
        null
    }

    /** SSDP is a LAN protocol: the description URL must resolve to the host that sent the packet. */
    fun discoveryLocation(value: String, sender: InetAddress): String? = try {
        val uri = parse(value)?.takeIf(::isSupported) ?: return null
        InetAddress.getAllByName(uri.host)
            .firstOrNull { it.address.contentEquals(sender.address) }
            ?.let { uri.toString() }
    } catch (_: Exception) {
        null
    }

    private fun parse(value: String): URI? = try {
        URI(value)
    } catch (_: URISyntaxException) {
        null
    }

    private fun isSupported(uri: URI): Boolean =
        uri.isAbsolute &&
            uri.host?.isNotBlank() == true &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port in MIN_TCP_PORT..MAX_TCP_PORT) &&
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))

    private const val MIN_TCP_PORT = 1
    private const val MAX_TCP_PORT = 65_535
}
