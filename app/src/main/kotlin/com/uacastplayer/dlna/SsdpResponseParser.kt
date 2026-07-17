package com.uacastplayer.dlna

/** Headers of a single SSDP M-SEARCH response datagram, lower-cased keys. */
data class SsdpResponse(val headers: Map<String, String>) {
    val location: String? get() = headers["location"]
}

/**
 * Pure header parsing for an SSDP response - no socket I/O. An SSDP response looks like an HTTP
 * status line followed by `Header: value` lines (CRLF-separated); we only care about the headers,
 * so the status line is simply ignored rather than validated.
 */
object SsdpResponseParser {

    fun parse(raw: String): SsdpResponse {
        val headers = mutableMapOf<String, String>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            val separator = trimmed.indexOf(':')
            if (separator <= 0) continue
            val key = trimmed.substring(0, separator).trim().lowercase()
            val value = trimmed.substring(separator + 1).trim()
            if (key.isNotEmpty()) headers[key] = value
        }
        return SsdpResponse(headers)
    }
}
