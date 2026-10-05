package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconResolverTest {
    @Test fun `no user pack means no automatic candidates`() {
        assertTrue(IconResolver.candidates("ch1").isEmpty())
    }

    @Test fun `missing channel ID means no candidate even with a pack`() {
        assertTrue(IconResolver.candidates(null, listOf("https://example.com/logos")).isEmpty())
    }

    @Test fun `blank channel ID means no candidate`() {
        assertTrue(IconResolver.candidates("  ", listOf("https://example.com/logos")).isEmpty())
    }

    @Test fun `user pack order is preserved without appending a default source`() {
        assertEquals(
            listOf(
                IconCandidate("https://first.example/logos/ch1.png"),
                IconCandidate("https://second.example/ch1.png"),
            ),
            IconResolver.candidates("ch1", listOf("https://first.example/logos/", "https://second.example")),
        )
    }

    @Test fun `equivalent pack addresses are deduplicated`() {
        assertEquals(
            listOf(IconCandidate("https://example.com/logos/ch1.png")),
            IconResolver.candidates("ch1", listOf("https://example.com/logos", " https://example.com/logos/ ")),
        )
    }

    @Test fun `unsafe pack addresses are skipped`() {
        assertEquals(
            listOf(IconCandidate("https://safe.example/ch1.png")),
            IconResolver.candidates("ch1", listOf("javascript:alert(1)", "file:///logos", "https://safe.example")),
        )
    }

    @Test fun `a user may explicitly add any valid host including a formerly built in source`() {
        assertEquals(
            listOf(IconCandidate("https://custom.example/logo/ch1.png")),
            IconResolver.candidates("ch1", listOf("https://custom.example/logo")),
        )
    }

    @Test fun `iconUrl trims a trailing slash on the base url`() {
        assertEquals("https://mycdn.com/logos/ch1.png", IconResolver.iconUrl("https://mycdn.com/logos/", "ch1"))
        assertEquals("https://mycdn.com/logos/ch1.png", IconResolver.iconUrl("https://mycdn.com/logos", "ch1"))
    }

    @Test fun `reserved and unicode channel IDs stay inside one path segment`() {
        assertEquals(
            "https://mycdn.com/logos/news%2Fde%3Fedition%3D%CE%B1%20%CE%B2.png",
            IconResolver.iconUrl("https://mycdn.com/logos/", "news/de?edition=α β"),
        )
    }
}
