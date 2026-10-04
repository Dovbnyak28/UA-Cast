package com.uacastplayer.icons

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** A channel logo from an explicitly user-added pack. */
data class IconCandidate(val url: String)

object IconResolver {

    /** No pack or no channel ID means no logo, never a playlist/EPG/CDN fallback. */
    fun candidates(
        tvgId: String?,
        customBaseUrls: List<String> = emptyList(),
    ): List<IconCandidate> = buildList {
        if (!tvgId.isNullOrBlank()) {
            customBaseUrls.mapNotNull(CustomIconSourcePolicy::canonicalize).forEach { baseUrl ->
                add(IconCandidate(iconUrl(baseUrl, tvgId)))
            }
        }
    }.distinctBy(IconCandidate::url)

    /** Joins a base URL (e.g. `https://mycdn.com/logos/`) with [tvgId] into a full icon URL. */
    fun iconUrl(baseUrl: String, tvgId: String): String =
        "${baseUrl.trimEnd('/')}/${encodePathSegment(tvgId)}.png"

    /** Encodes provider-controlled IDs as one path segment, never as path/query syntax. */
    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
