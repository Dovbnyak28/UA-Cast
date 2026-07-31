package com.uacastplayer.icons

/**
 * Builds the in-memory icon cache key from the same inputs [IconResolver.candidates] uses to build
 * the candidate URL chain - two channels with identical tvgLogo/epgIconUrl/tvgId resolve to the
 * same icon, so they should share one cache entry. Joined with a control character that can't
 * appear in a URL or tvg-id, so a field boundary can't be spoofed by a value containing the
 * delimiter (a plain space or "|" could plausibly show up in a malformed URL).
 */
object IconMemoryCacheKey {
    private const val DELIMITER = ""

    fun of(tvgLogo: String?, epgIconUrl: String?, tvgId: String?): String =
        listOf(tvgLogo.orEmpty(), epgIconUrl.orEmpty(), tvgId.orEmpty()).joinToString(DELIMITER)
}
