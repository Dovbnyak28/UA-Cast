package com.uacastplayer.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgSourceAutoDetectTest {

    @Test
    fun `empty epgUrls is ignored`() {
        val action = EpgSourceAutoDetect.decide(epgUrls = emptyList(), hasChosenEpgSource = false, currentUrl = null)
        assertEquals(EpgSourceAutoDetect.Action.Ignore, action)
    }

    @Test
    fun `a single URL is applied when the user has not chosen manually`() {
        val action = EpgSourceAutoDetect.decide(
            epgUrls = listOf("http://example.com/epg.xml"),
            hasChosenEpgSource = false,
            currentUrl = null,
        )
        assertEquals(EpgSourceAutoDetect.Action.Apply("http://example.com/epg.xml"), action)
    }

    @Test
    fun `the first of multiple URLs is applied`() {
        val action = EpgSourceAutoDetect.decide(
            epgUrls = listOf("http://a.com/epg.xml", "http://b.com/epg.xml"),
            hasChosenEpgSource = false,
            currentUrl = null,
        )
        assertEquals(EpgSourceAutoDetect.Action.Apply("http://a.com/epg.xml"), action)
    }

    @Test
    fun `a manually chosen source is only suggested, not overridden`() {
        val action = EpgSourceAutoDetect.decide(
            epgUrls = listOf("http://example.com/epg.xml"),
            hasChosenEpgSource = true,
            currentUrl = null,
        )
        assertEquals(EpgSourceAutoDetect.Action.Suggest("http://example.com/epg.xml"), action)
    }

    @Test
    fun `the same URL as the current source is ignored even if not chosen manually`() {
        val action = EpgSourceAutoDetect.decide(
            epgUrls = listOf("http://example.com/epg.xml"),
            hasChosenEpgSource = false,
            currentUrl = "http://example.com/epg.xml",
        )
        assertEquals(EpgSourceAutoDetect.Action.Ignore, action)
    }

    @Test
    fun `the same URL as the current source is ignored when chosen manually too`() {
        val action = EpgSourceAutoDetect.decide(
            epgUrls = listOf("http://example.com/epg.xml"),
            hasChosenEpgSource = true,
            currentUrl = "http://example.com/epg.xml",
        )
        assertEquals(EpgSourceAutoDetect.Action.Ignore, action)
    }

    @Test
    fun `blank URLs in the list are skipped in favor of the first non-blank one`() {
        val action = EpgSourceAutoDetect.decide(
            epgUrls = listOf(" ", "http://example.com/epg.xml"),
            hasChosenEpgSource = false,
            currentUrl = null,
        )
        assertEquals(EpgSourceAutoDetect.Action.Apply("http://example.com/epg.xml"), action)
    }
}
