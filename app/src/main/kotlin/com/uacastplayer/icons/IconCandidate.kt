package com.uacastplayer.icons

/**
 * [Fetchable] candidates may be downloaded fresh over the network; [CacheOnly] (the CDN
 * fallback-by-tvg-id) may only ever be shown from what's already on disk - speculatively
 * fetching an icon nobody asked for defeats the point of a fallback of last resort.
 */
sealed class IconCandidate {
    abstract val url: String
    data class Fetchable(override val url: String) : IconCandidate()
    data class CacheOnly(override val url: String) : IconCandidate()
}

object IconResolver {

    fun candidates(
        tvgLogo: String?,
        epgIconUrl: String?,
        tvgId: String?,
        cdnFallbackUrl: (tvgId: String) -> String,
    ): List<IconCandidate> = buildList {
        if (!tvgLogo.isNullOrBlank()) add(IconCandidate.Fetchable(tvgLogo))
        if (!epgIconUrl.isNullOrBlank()) add(IconCandidate.Fetchable(epgIconUrl))
        if (!tvgId.isNullOrBlank()) add(IconCandidate.CacheOnly(cdnFallbackUrl(tvgId)))
    }
}
