package com.uacastplayer.proxy

import java.net.URI
import java.net.URISyntaxException

/**
 * Rewrites an HLS playlist so every reference it makes (segment URIs, `URI="..."` attributes on
 * tags like EXT-X-KEY/EXT-X-MEDIA/EXT-X-MAP) points at our local proxy instead of the origin.
 * References use a scheme we don't proxy (anything but http/https - `skd://` FairPlay key URIs
 * are the common case) are left untouched rather than rewritten.
 */
object M3u8Rewriter {

    private val uriAttributePattern = Regex("""URI="([^"]*)"""")

    /**
     * [baseUrl] must be the *final* URL after following redirects, since that's what relative
     * references are actually relative to. [mapUrl] receives an absolute http(s) URL and returns
     * the local proxy URL to substitute, or null to leave the reference unrewritten.
     */
    fun rewrite(playlist: String, baseUrl: String, mapUrl: (String) -> String?): String {
        return playlist.removePrefix(UTF8_BOM).split("\n").joinToString("\n") { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.isBlank() -> line
                line.startsWith("#") -> rewriteTagLine(line, baseUrl, mapUrl)
                else -> rewriteUriLine(line, baseUrl, mapUrl)
            }
        }
    }

    private fun rewriteTagLine(line: String, baseUrl: String, mapUrl: (String) -> String?): String =
        uriAttributePattern.replace(line) { match ->
            val resolved = resolveAndMap(match.groupValues[1], baseUrl, mapUrl) ?: match.groupValues[1]
            """URI="$resolved""""
        }

    private fun rewriteUriLine(line: String, baseUrl: String, mapUrl: (String) -> String?): String =
        resolveAndMap(line.trim(), baseUrl, mapUrl) ?: line

    private fun resolveAndMap(reference: String, baseUrl: String, mapUrl: (String) -> String?): String? {
        val absolute = reference.takeUnless(String::isBlank)?.let { resolveUrl(baseUrl, it) }
        return absolute?.let(mapUrl)
    }

    /** IPTV playlists routinely carry unencoded spaces in paths ("live tv/chan 1.ts"), which
     * [URI] rejects outright - the retry with spaces percent-encoded only ever runs where the
     * strict parse already failed, so it can only turn a dropped (unrewritten) reference into a
     * working one, never break a URL the strict parse accepted. */
    fun resolveUrl(baseUrl: String, reference: String): String? =
        resolveStrict(baseUrl, reference)
            ?: resolveStrict(encodeSpaces(baseUrl), encodeSpaces(reference))

    private fun resolveStrict(baseUrl: String, reference: String): String? = try {
        URI(baseUrl).resolve(URI(reference)).takeIf(::isSupportedHttpUrl)?.toString()
    } catch (_: URISyntaxException) {
        null
    }

    private fun encodeSpaces(url: String): String = url.replace(" ", "%20")

    private fun isSupportedHttpUrl(uri: URI): Boolean =
        uri.isAbsolute &&
            uri.host?.isNotBlank() == true &&
            uri.port in -1..MAX_TCP_PORT &&
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))

    private const val MAX_TCP_PORT = 65_535
}
