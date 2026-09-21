package com.uacastplayer.icons

import java.net.URI

/**
 * Canonicalizes provider-supplied icon URLs before they enter the resolver/cache chain.
 *
 * IPTV playlists are commonly generated for a browser and contain protocol-relative URLs or
 * HTML-escaped ampersands. OkHttp rejects the former and requests the wrong path for the latter,
 * so one malformed primary candidate could make every visible row fall back to initials even when
 * the same logo was otherwise reachable. Query strings are deliberately preserved because many
 * providers use signed URLs for artwork.
 */
object IconUrlPolicy {

    private const val MAX_URL_LENGTH = 8_192
    private const val PROTOCOL_RELATIVE_PREFIX = "//"

    /** Returns a safe HTTP(S) URL, or null when the playlist value is unusable. */
    fun canonicalize(raw: String): String? {
        val value = raw.trim()
            .replace("&amp;", "&", ignoreCase = true)
            .takeIf { it.isNotEmpty() && it.length <= MAX_URL_LENGTH }
        val absolute = value?.let { candidate ->
            if (candidate.startsWith(PROTOCOL_RELATIVE_PREFIX)) "https:$candidate" else candidate
        }
        val uri = absolute?.let { candidate -> runCatching { URI(candidate) }.getOrNull() }
        return uri
            ?.takeIf { candidate ->
                candidate.isAbsolute &&
                    candidate.userInfo == null &&
                    !candidate.host.isNullOrBlank() &&
                    candidate.port in -1..MAX_TCP_PORT &&
                    (candidate.scheme.equals("http", ignoreCase = true) ||
                        candidate.scheme.equals("https", ignoreCase = true))
            }
            ?.normalize()
            ?.toString()
    }

    private const val MAX_TCP_PORT = 65_535
}
