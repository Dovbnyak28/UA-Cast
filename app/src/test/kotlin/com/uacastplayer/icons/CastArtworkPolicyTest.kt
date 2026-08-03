package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every case goes through a real [IconResolver.candidates] chain rather than a hand-built list. The
 * property under test is that a receiver is shown *the same logo the phone resolves*, and a test
 * fed a list someone wrote by hand would keep passing after the chain's ordering changed underneath
 * it - which is exactly the bug it exists to catch.
 */
class CastArtworkPolicyTest {

    private val cdnBuilder: (String) -> String = { id -> "https://cdn.example.com/$id.png" }

    private fun artworkFor(
        tvgLogo: String? = null,
        epgIconUrl: String? = null,
        tvgId: String? = null,
        customBaseUrls: List<String> = emptyList(),
    ): String? = CastArtworkPolicy.artworkUrl(
        IconResolver.candidates(tvgLogo, epgIconUrl, tvgId, customBaseUrls, cdnBuilder),
    )

    @Test
    fun `tvg-logo wins when the playlist carries one`() {
        assertEquals(
            "http://logo",
            artworkFor(tvgLogo = "http://logo", epgIconUrl = "http://epg-icon", tvgId = "ch1"),
        )
    }

    /** The case this policy was written for: a playlist entry with only a tvg-id used to show a
     * logo in the channel list and a bare title on the TV. */
    @Test
    fun `the EPG icon carries a channel that has no tvg-logo`() {
        assertEquals("http://epg-icon", artworkFor(epgIconUrl = "http://epg-icon", tvgId = "ch1"))
    }

    @Test
    fun `a user-added source is used when neither the playlist nor the EPG has a logo`() {
        assertEquals(
            "https://mycdn.example/logos/ch1.png",
            artworkFor(tvgId = "ch1", customBaseUrls = listOf("https://mycdn.example/logos/")),
        )
    }

    /**
     * The built-in CDN fallback is the only cache-only candidate, so a channel with nothing but a
     * tvg-id produces a chain that is non-empty and still yields no artwork. Sending that URL would
     * be a guess, and a receiver reports a broken image as an error rather than falling back to its
     * no-artwork layout - see [CastArtworkPolicy].
     */
    @Test
    fun `the cache-only CDN fallback is never sent to a receiver`() {
        val candidates = IconResolver.candidates(null, null, "ch1", emptyList(), cdnBuilder)
        assertEquals(listOf(IconCandidate.CacheOnly("https://cdn.example.com/ch1.png")), candidates)
        assertNull(CastArtworkPolicy.artworkUrl(candidates))
    }

    @Test
    fun `a channel with no logo, no EPG match and no tvg-id has no artwork`() {
        assertNull(artworkFor())
    }

    /** Blank is dropped by the chain itself, and the point here is that the policy inherits that
     * rather than passing an empty string on to [com.uacastplayer.cast.CastMediaLoader]. */
    @Test
    fun `a blank tvg-logo does not shadow a usable EPG icon`() {
        assertEquals("http://epg-icon", artworkFor(tvgLogo = "   ", epgIconUrl = "http://epg-icon"))
    }
}
