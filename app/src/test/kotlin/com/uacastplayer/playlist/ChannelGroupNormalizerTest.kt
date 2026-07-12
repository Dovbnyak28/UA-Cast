package com.uacastplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelGroupNormalizerTest {

    @Test
    fun `null or blank group title is ungrouped`() {
        assertEquals(ChannelGroup.Ungrouped, ChannelGroupNormalizer.normalize(null))
        assertEquals(ChannelGroup.Ungrouped, ChannelGroupNormalizer.normalize(""))
        assertEquals(ChannelGroup.Ungrouped, ChannelGroupNormalizer.normalize("   "))
    }

    @Test
    fun `recognizes English Russian and Ukrainian aliases for movies`() {
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_MOVIES), ChannelGroupNormalizer.normalize("Movies"))
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_MOVIES), ChannelGroupNormalizer.normalize("Кино"))
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_MOVIES), ChannelGroupNormalizer.normalize("Фильмы"))
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_MOVIES), ChannelGroupNormalizer.normalize("Фільми"))
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_MOVIES), ChannelGroupNormalizer.normalize("Películas"))
    }

    @Test
    fun `matching is case insensitive and trims whitespace`() {
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_SPORTS), ChannelGroupNormalizer.normalize("  SPORT  "))
    }

    @Test
    fun `treats yo and ye as equivalent for Russian aliases`() {
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_KIDS), ChannelGroupNormalizer.normalize("Мультфильмы"))
    }

    @Test
    fun `unrecognized group titles are preserved verbatim as custom`() {
        val result = ChannelGroupNormalizer.normalize("USA HD Premium")
        assertEquals(ChannelGroup.Custom("USA HD Premium"), result)
    }

    @Test
    fun `custom groups keep original casing and trim only outer whitespace`() {
        val result = ChannelGroupNormalizer.normalize("  My Custom Group  ")
        assertEquals(ChannelGroup.Custom("My Custom Group"), result)
    }

    @Test
    fun `recognizes news sports kids and documentary categories`() {
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_NEWS), ChannelGroupNormalizer.normalize("News"))
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_SPORTS), ChannelGroupNormalizer.normalize("Спорт"))
        assertEquals(ChannelGroup.Known(ChannelGroup.KEY_KIDS), ChannelGroupNormalizer.normalize("Kids"))
        assertEquals(
            ChannelGroup.Known(ChannelGroup.KEY_DOCUMENTARY),
            ChannelGroupNormalizer.normalize("Документальные"),
        )
    }
}
