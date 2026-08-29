package com.uacastplayer.playlist

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun `cancellation probe stops a large parse before the remaining lines are consumed`() {
        val playlist = buildString {
            appendLine("#EXTM3U")
            repeat(1_000) { index ->
                appendLine("#EXTINF:-1,Channel $index")
                appendLine("http://example.com/$index.ts")
            }
        }
        var checks = 0

        val failure = assertThrows(CancellationException::class.java) {
            M3uParser.parse(playlist) {
                checks++
                if (checks == 3) throw CancellationException("superseded")
            }
        }

        assertEquals("superseded", failure.message)
        assertTrue(checks >= 3)
    }

    @Test
    fun `parses a basic entry with quoted attributes`() {
        val result = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="ch1" tvg-name="Channel One" tvg-logo="http://x/1.png" group-title="News",Channel One
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals(1, result.channels.size)
        val channel = result.channels[0]
        assertEquals("Channel One", channel.displayName)
        assertEquals("http://example.com/1.m3u8", channel.streamUrl)
        assertEquals("ch1", channel.tvgId)
        assertEquals("Channel One", channel.tvgName)
        assertEquals("http://x/1.png", channel.tvgLogo)
        assertEquals("News", channel.groupTitle)
        assertEquals(0, result.skippedLineCount)
    }

    @Test
    fun `accepts lower case extinf and extgrp directives`() {
        val result = M3uParser.parse(
            """
            #extm3u
            #extinf:-1,Channel One
            #extgrp:News
            http://example.com/1.m3u8
            """.trimIndent()
        )

        assertEquals(1, result.channels.size)
        assertEquals("Channel One", result.channels[0].displayName)
        assertEquals("News", result.channels[0].groupTitle)
    }

    @Test
    fun `strips a leading UTF-8 BOM`() {
        val result = M3uParser.parse(
            "﻿#EXTM3U\n#EXTINF:-1,Channel\nhttp://example.com/1.m3u8"
        )
        assertEquals(1, result.channels.size)
        assertEquals("Channel", result.channels[0].displayName)
    }

    @Test
    fun `parses unquoted attribute values`() {
        val result = M3uParser.parse(
            "#EXTINF:-1 tvg-id=ch1 group-title=News,Channel One\nhttp://example.com/1.m3u8"
        )
        val channel = result.channels[0]
        assertEquals("ch1", channel.tvgId)
        assertEquals("News", channel.groupTitle)
    }

    @Test
    fun `trims surrounding whitespace from quoted attributes`() {
        val result = M3uParser.parse(
            "#EXTINF:-1 tvg-id=\" ch1 \" tvg-name=\" Channel One \" " +
                "tvg-logo=\" https://example.com/logo.png \" group-title=\" News \"\n" +
                "http://example.com/1.m3u8",
        )
        val channel = result.channels.single()

        assertEquals("ch1", channel.tvgId)
        assertEquals("Channel One", channel.tvgName)
        assertEquals("https://example.com/logo.png", channel.tvgLogo)
        assertEquals("News", channel.groupTitle)
    }

    @Test
    fun `comma inside a quoted attribute does not split the display name`() {
        val result = M3uParser.parse(
            """#EXTINF:-1 group-title="Kids, Family",Channel One""" + "\nhttp://example.com/1.m3u8"
        )
        val channel = result.channels[0]
        assertEquals("Kids, Family", channel.groupTitle)
        assertEquals("Channel One", channel.displayName)
    }

    @Test
    fun `EXTGRP provides a fallback group when group-title attribute is absent`() {
        val result = M3uParser.parse(
            """
            #EXTINF:-1,Channel One
            #EXTGRP:Movies
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals("Movies", result.channels[0].groupTitle)
    }

    @Test
    fun `group-title attribute takes priority over EXTGRP`() {
        val result = M3uParser.parse(
            """
            #EXTINF:-1 group-title="News",Channel One
            #EXTGRP:Movies
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals("News", result.channels[0].groupTitle)
    }

    @Test
    fun `falls back to tvg-name when the display name is missing`() {
        val result = M3uParser.parse(
            """#EXTINF:-1 tvg-name="Fallback Name",""" + "\nhttp://example.com/1.m3u8"
        )
        assertEquals(1, result.channels.size)
        assertEquals("Fallback Name", result.channels[0].displayName)
    }

    @Test
    fun `falls back to tvg-id when both display name and tvg-name are missing`() {
        val result = M3uParser.parse(
            """#EXTINF:-1 tvg-id="ch-42",""" + "\nhttp://example.com/1.m3u8"
        )
        assertEquals("ch-42", result.channels[0].displayName)
    }

    @Test
    fun `skips an entry with no usable title at all`() {
        val result = M3uParser.parse("#EXTINF:-1,\nhttp://example.com/1.m3u8")
        assertEquals(0, result.channels.size)
        assertEquals(1, result.skippedLineCount)
    }

    @Test
    fun `counts an orphan URL line with no preceding EXTINF as skipped`() {
        val result = M3uParser.parse(
            """
            #EXTM3U
            http://example.com/orphan.m3u8
            #EXTINF:-1,Channel One
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals(1, result.channels.size)
        assertEquals(1, result.skippedLineCount)
    }

    @Test
    fun `counts a trailing EXTINF with no following URL as skipped`() {
        val result = M3uParser.parse(
            """
            #EXTINF:-1,Channel One
            http://example.com/1.m3u8
            #EXTINF:-1,Orphan Extinf
            """.trimIndent()
        )
        assertEquals(1, result.channels.size)
        assertEquals(1, result.skippedLineCount)
    }

    @Test
    fun `two consecutive EXTINF lines count the first as skipped`() {
        val result = M3uParser.parse(
            """
            #EXTINF:-1,First
            #EXTINF:-1,Second
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals(1, result.channels.size)
        assertEquals("Second", result.channels[0].displayName)
        assertEquals(1, result.skippedLineCount)
    }

    @Test
    fun `unrecognized comment tags are ignored without affecting the skip count`() {
        val result = M3uParser.parse(
            """
            #EXTM3U
            #EXTVLCOPT:network-caching=1000
            #EXTINF:-1,Channel One
            #KODIPROP:inputstream=x
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals(1, result.channels.size)
        assertEquals(0, result.skippedLineCount)
    }

    @Test
    fun `parses multiple channels in order`() {
        val result = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,First
            http://example.com/1.m3u8
            #EXTINF:-1,Second
            http://example.com/2.m3u8
            """.trimIndent()
        )
        assertEquals(listOf("First", "Second"), result.channels.map { it.displayName })
    }

    @Test
    fun `missing attributes resolve to null rather than empty strings`() {
        val result = M3uParser.parse("#EXTINF:-1,Channel\nhttp://example.com/1.m3u8")
        val channel = result.channels[0]
        assertNull(channel.tvgId)
        assertNull(channel.tvgName)
        assertNull(channel.tvgLogo)
        assertNull(channel.groupTitle)
    }

    @Test
    fun `blank lines between entries are ignored`() {
        val result = M3uParser.parse(
            "\n\n#EXTINF:-1,Channel\n\nhttp://example.com/1.m3u8\n\n"
        )
        assertEquals(1, result.channels.size)
        assertEquals(0, result.skippedLineCount)
    }

    @Test
    fun `EXTVLCOPT provides a user-agent and referrer for the next channel`() {
        val result = M3uParser.parse(
            """
            #EXTINF:-1,Channel One
            #EXTVLCOPT:http-user-agent=CustomAgent/1.0
            #EXTVLCOPT:http-referrer=https://example.com/
            http://example.com/1.m3u8
            """.trimIndent()
        )
        val channel = result.channels[0]
        assertEquals("CustomAgent/1.0", channel.userAgent)
        assertEquals("https://example.com/", channel.referrer)
    }

    @Test
    fun `EXTVLCOPT tag and option names are matched case-insensitively`() {
        val result = M3uParser.parse(
            """
            #EXTINF:-1,Channel One
            #extvlcopt:HTTP-User-Agent=CustomAgent/1.0
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals("CustomAgent/1.0", result.channels[0].userAgent)
    }

    @Test
    fun `EXTVLCOPT headers do not leak into a channel that had none`() {
        val result = M3uParser.parse(
            """
            #EXTINF:-1,First
            #EXTVLCOPT:http-user-agent=CustomAgent/1.0
            http://example.com/1.m3u8
            #EXTINF:-1,Second
            http://example.com/2.m3u8
            """.trimIndent()
        )
        assertEquals("CustomAgent/1.0", result.channels[0].userAgent)
        assertNull(result.channels[1].userAgent)
        assertNull(result.channels[1].referrer)
    }

    @Test
    fun `channels without an EXTVLCOPT tag have null user-agent and referrer`() {
        val result = M3uParser.parse("#EXTINF:-1,Channel\nhttp://example.com/1.m3u8")
        val channel = result.channels[0]
        assertNull(channel.userAgent)
        assertNull(channel.referrer)
    }

    @Test
    fun `EXTVLCOPT with an unrecognized option is ignored without affecting the skip count`() {
        val result = M3uParser.parse(
            """
            #EXTINF:-1,Channel
            #EXTVLCOPT:network-caching=1000
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals(1, result.channels.size)
        assertEquals(0, result.skippedLineCount)
        assertNull(result.channels[0].userAgent)
    }

    @Test
    fun `parses url-tvg from the EXTM3U header`() {
        val result = M3uParser.parse(
            """
            #EXTM3U url-tvg="http://example.com/epg.xml"
            #EXTINF:-1,Channel
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals(listOf("http://example.com/epg.xml"), result.epgUrls)
    }

    @Test
    fun `parses epg metadata from a lower-case EXTM3U header`() {
        val result = M3uParser.parse(
            "#extm3u url-tvg=\"https://example.com/epg.xml\"\n" +
                "#EXTINF:-1,Channel\nhttps://example.com/stream.ts\n",
        )

        assertEquals(listOf("https://example.com/epg.xml"), result.epgUrls)
    }

    @Test
    fun `parses x-tvg-url case-insensitively`() {
        val result = M3uParser.parse(
            """#EXTM3U X-TVG-URL="http://example.com/epg.xml"""" + "\n#EXTINF:-1,Channel\nhttp://example.com/1.m3u8"
        )
        assertEquals(listOf("http://example.com/epg.xml"), result.epgUrls)
    }

    @Test
    fun `splits and trims a comma-separated list of EPG URLs`() {
        val result = M3uParser.parse(
            """#EXTM3U url-tvg="http://a.com/epg.xml, http://b.com/epg.xml"""" +
                "\n#EXTINF:-1,Channel\nhttp://example.com/1.m3u8"
        )
        assertEquals(listOf("http://a.com/epg.xml", "http://b.com/epg.xml"), result.epgUrls)
    }

    @Test
    fun `no EXTM3U header yields an empty epgUrls list`() {
        val result = M3uParser.parse("#EXTINF:-1,Channel\nhttp://example.com/1.m3u8")
        assertEquals(emptyList<String>(), result.epgUrls)
    }

    @Test
    fun `EXTM3U header without url-tvg or x-tvg-url yields an empty epgUrls list`() {
        val result = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,Channel
            http://example.com/1.m3u8
            """.trimIndent()
        )
        assertEquals(emptyList<String>(), result.epgUrls)
    }

    /** Windows-authored playlists are common, and a stray CR left on the end of a url would make
     * every stream unplayable - this is what the line splitting has to get right above all else. */
    @Test
    fun `CRLF line endings leave no carriage return on any value`() {
        val result = M3uParser.parse(
            "#EXTM3U\r\n" +
                "#EXTINF:-1 tvg-id=\"ch1\" group-title=\"News\",Channel One\r\n" +
                "http://example.com/1.m3u8\r\n",
        )
        assertEquals(1, result.channels.size)
        val channel = result.channels[0]
        assertEquals("Channel One", channel.displayName)
        assertEquals("http://example.com/1.m3u8", channel.streamUrl)
        assertEquals("News", channel.groupTitle)
        assertEquals("ch1", channel.tvgId)
        assertEquals(0, result.skippedLineCount)
    }

    @Test
    fun `mixed CRLF and LF endings in one playlist parse identically`() {
        val result = M3uParser.parse(
            "#EXTM3U\r\n" +
                "#EXTINF:-1,First\n" +
                "http://example.com/1.m3u8\r\n" +
                "#EXTINF:-1,Second\r\n" +
                "http://example.com/2.m3u8\n",
        )
        assertEquals(listOf("First", "Second"), result.channels.map { it.displayName })
        assertEquals(
            listOf("http://example.com/1.m3u8", "http://example.com/2.m3u8"),
            result.channels.map { it.streamUrl },
        )
        assertEquals(0, result.skippedLineCount)
    }

    /** A lone CR is treated as a line separator too. `\r` is a control character that cannot appear
     * inside a real channel name or url, so reading it as a classic-Mac line ending is the only
     * interpretation that can produce a usable playlist rather than one unparseable line. */
    @Test
    fun `a lone carriage return separates lines`() {
        val result = M3uParser.parse(
            "#EXTM3U\r#EXTINF:-1,Channel One\rhttp://example.com/1.m3u8\r",
        )
        assertEquals(1, result.channels.size)
        assertEquals("Channel One", result.channels[0].displayName)
        assertEquals("http://example.com/1.m3u8", result.channels[0].streamUrl)
    }
}
