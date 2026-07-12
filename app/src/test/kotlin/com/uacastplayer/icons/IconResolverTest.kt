package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Test

class IconResolverTest {

    private val cdnBuilder: (String) -> String = { id -> "https://cdn.example.com/$id.png" }

    @Test
    fun `prefers tvg-logo first`() {
        val result = IconResolver.candidates(
            tvgLogo = "http://logo",
            epgIconUrl = "http://epg-icon",
            tvgId = "ch1",
            cdnFallbackUrl = cdnBuilder,
        )
        assertEquals(IconCandidate.Fetchable("http://logo"), result[0])
    }

    @Test
    fun `falls back to EPG icon when tvg-logo is absent`() {
        val result = IconResolver.candidates(
            tvgLogo = null,
            epgIconUrl = "http://epg-icon",
            tvgId = "ch1",
            cdnFallbackUrl = cdnBuilder,
        )
        assertEquals(IconCandidate.Fetchable("http://epg-icon"), result[0])
    }

    @Test
    fun `CDN fallback is cache-only and comes last`() {
        val result = IconResolver.candidates(
            tvgLogo = "http://logo",
            epgIconUrl = "http://epg-icon",
            tvgId = "ch1",
            cdnFallbackUrl = cdnBuilder,
        )
        assertEquals(IconCandidate.CacheOnly("https://cdn.example.com/ch1.png"), result.last())
        assertEquals(3, result.size)
    }

    @Test
    fun `blank values are treated as absent`() {
        val result = IconResolver.candidates(
            tvgLogo = "   ",
            epgIconUrl = "",
            tvgId = "ch1",
            cdnFallbackUrl = cdnBuilder,
        )
        assertEquals(1, result.size)
        assertEquals(IconCandidate.CacheOnly("https://cdn.example.com/ch1.png"), result[0])
    }

    @Test
    fun `no signals at all yields no candidates`() {
        val result = IconResolver.candidates(null, null, null, cdnBuilder)
        assertEquals(emptyList<IconCandidate>(), result)
    }
}
