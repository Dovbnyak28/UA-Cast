package com.uacastplayer.dlna

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

    private fun parse(value: String): URI? = try {
        URI(value)
    } catch (_: URISyntaxException) {
        null
    }

    private fun isSupported(uri: URI): Boolean =
        uri.isAbsolute &&
            uri.host?.isNotBlank() == true &&
            uri.userInfo == null &&
            uri.port in -1..MAX_TCP_PORT &&
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))

    private const val MAX_TCP_PORT = 65_535
}
