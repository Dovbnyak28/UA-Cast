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

    /** Fast admission for the UDP loop. Numeric IPv4 locations can be checked without a resolver;
     * hostname resolution is deferred to discoveryLocation on a description-fetch worker. */
    fun discoveryCandidate(value: String, sender: InetAddress): String? = parse(value)
        ?.takeIf(::isSupported)
        ?.takeIf { uri -> uri.host?.let { candidateHostMatchesSender(it, sender) } == true }
        ?.toString()

    private fun candidateHostMatchesSender(host: String, sender: InetAddress): Boolean {
        if (!host.all { it in '0'..'9' || it == '.' }) return true
        val parts = host.split('.')
        val octets = parts.mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 0..IPV4_OCTET_MAX }?.toByte() }
        return parts.size == IPV4_OCTET_COUNT && octets.size == IPV4_OCTET_COUNT &&
            octets.toByteArray().contentEquals(sender.address)
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
    private const val IPV4_OCTET_COUNT = 4
    private const val IPV4_OCTET_MAX = 255
}
