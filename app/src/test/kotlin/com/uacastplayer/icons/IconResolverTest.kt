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
    fun `normalizes protocol relative and HTML escaped provider URLs`() {
        val result = IconResolver.candidates(
            tvgLogo = " //cdn.example.com/logo.png?token=a&amp;b=1 ",
            epgIconUrl = "https://epg.example.com/icon.png",
            tvgId = null,
            cdnFallbackUrl = cdnBuilder,
        )
        assertEquals(
            IconCandidate.Fetchable("https://cdn.example.com/logo.png?token=a&b=1"),
            result[0],
        )
    }

    @Test
    fun `skips malformed primary URL so later candidates can be tried`() {
        val result = IconResolver.candidates(
            tvgLogo = "javascript:alert(1)",
            epgIconUrl = "https://epg.example.com/icon.png",
            tvgId = null,
            cdnFallbackUrl = cdnBuilder,
        )
        assertEquals(listOf(IconCandidate.Fetchable("https://epg.example.com/icon.png")), result)
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
        val result = IconResolver.candidates(null, null, null, cdnFallbackUrl = cdnBuilder)
        assertEquals(emptyList<IconCandidate>(), result)
    }

    @Test
    fun `custom sources are fetchable and tried before the CDN fallback`() {
        val result = IconResolver.candidates(
            tvgLogo = null,
            epgIconUrl = null,
            tvgId = "ch1",
            customBaseUrls = listOf("https://mycdn.com/logos/", "https://other.example.com/icons"),
            cdnFallbackUrl = cdnBuilder,
        )
        assertEquals(
            listOf(
                IconCandidate.Fetchable("https://mycdn.com/logos/ch1.png"),
                IconCandidate.Fetchable("https://other.example.com/icons/ch1.png"),
                IconCandidate.CacheOnly("https://cdn.example.com/ch1.png"),
            ),
            result,
        )
    }

    @Test
    fun `custom sources are skipped without a tvg-id, same as the CDN fallback`() {
        val result = IconResolver.candidates(
            tvgLogo = "http://logo",
            epgIconUrl = null,
            tvgId = null,
            customBaseUrls = listOf("https://mycdn.com/logos/"),
            cdnFallbackUrl = cdnBuilder,
        )
        assertEquals(listOf(IconCandidate.Fetchable("http://logo")), result)
    }

    @Test
    fun `deduplicates equivalent candidates while keeping the highest priority kind`() {
        val result = IconResolver.candidates(
            tvgLogo = " https://cdn.example.com/ch1.png ",
            epgIconUrl = "https://cdn.example.com/ch1.png",
            tvgId = "ch1",
            customBaseUrls = listOf("https://cdn.example.com"),
            cdnFallbackUrl = cdnBuilder,
        )

        assertEquals(
            listOf(IconCandidate.Fetchable("https://cdn.example.com/ch1.png")),
            result,
        )
    }

    @Test
    fun `iconUrl trims a trailing slash on the base url`() {
        assertEquals("https://mycdn.com/logos/ch1.png", IconResolver.iconUrl("https://mycdn.com/logos/", "ch1"))
        assertEquals("https://mycdn.com/logos/ch1.png", IconResolver.iconUrl("https://mycdn.com/logos", "ch1"))
    }

    @Test
    fun `encodes reserved and unicode tvg-id characters inside one path segment`() {
        assertEquals(
            "https://mycdn.com/logos/news%2Fde%3Fedition%3D%CE%B1%20%CE%B2.png",
            IconResolver.iconUrl("https://mycdn.com/logos/", "news/de?edition=α β"),
        )
    }
}
