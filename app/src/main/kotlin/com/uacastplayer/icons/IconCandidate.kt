package com.uacastplayer.icons

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * [Fetchable] candidates may be downloaded fresh over the network; [CacheOnly] (the CDN
 * fallback-by-tvg-id) may only ever be shown from what's already on disk - speculatively
 * fetching an icon nobody asked for defeats the point of a fallback of last resort.
 */
sealed interface IconCandidate {
    val url: String
    data class Fetchable(override val url: String) : IconCandidate
    data class CacheOnly(override val url: String) : IconCandidate
}

object IconResolver {

    /** Base URL for the app's own last-resort CDN fallback; see [cdnFallbackUrl]. */
    const val BUILT_IN_ICON_SOURCE_BASE_URL = "https://cdn.epg.one/logo/"

    /**
     * [tvgLogo]/[epgIconUrl] are tried first (both actively fetched), then any user-added
     * [customBaseUrls] (also actively fetched - the user explicitly opted into these, unlike the
     * app's own CDN guess), then the built-in CDN fallback last, cache-only. Custom sources and
     * the CDN fallback both need [tvgId] to build a URL, so both are skipped without it.
     */
    fun candidates(
        tvgLogo: String?,
        epgIconUrl: String?,
        tvgId: String?,
        customBaseUrls: List<String> = emptyList(),
        cdnFallbackUrl: (tvgId: String) -> String,
    ): List<IconCandidate> = buildList {
        tvgLogo?.let(IconUrlPolicy::canonicalize)?.let { add(IconCandidate.Fetchable(it)) }
        epgIconUrl?.let(IconUrlPolicy::canonicalize)?.let { add(IconCandidate.Fetchable(it)) }
        if (!tvgId.isNullOrBlank()) {
            customBaseUrls.forEach { baseUrl -> add(IconCandidate.Fetchable(iconUrl(baseUrl, tvgId))) }
            add(IconCandidate.CacheOnly(cdnFallbackUrl(tvgId)))
        }
    }.distinctBy(IconCandidate::url)

    /** Joins a base URL (e.g. `https://mycdn.com/logos/`) with [tvgId] into a full icon URL. */
    fun iconUrl(baseUrl: String, tvgId: String): String =
        "${baseUrl.trimEnd('/')}/${encodePathSegment(tvgId)}.png"

    /** Encodes provider-controlled IDs as one path segment, never as path/query syntax. */
    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
