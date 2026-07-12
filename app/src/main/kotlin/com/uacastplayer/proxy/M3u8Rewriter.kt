package com.uacastplayer.proxy

import java.net.URI

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
        return playlist.split("\n").joinToString("\n") { rawLine ->
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
        if (reference.isBlank()) return null
        val absolute = resolveUrl(baseUrl, reference) ?: return null
        if (!isSupportedScheme(absolute)) return null
        return mapUrl(absolute)
    }

    fun resolveUrl(baseUrl: String, reference: String): String? = try {
        URI(baseUrl).resolve(URI(reference)).toString()
    } catch (_: Exception) {
        null
    }

    private fun isSupportedScheme(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme == "http" || scheme == "https"
    }
}
