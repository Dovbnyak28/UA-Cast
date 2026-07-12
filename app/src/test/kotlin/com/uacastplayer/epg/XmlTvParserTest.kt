package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlTvParserTest {

    private fun parse(xml: String): XmlTvParseResult =
        XmlTvParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test
    fun `parses a channel with multiple display names and an icon`() {
        val result = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="bbc.one.uk">
                <display-name>BBC One</display-name>
                <display-name>BBC 1</display-name>
                <icon src="http://example.com/bbc.png"/>
              </channel>
            </tv>
            """.trimIndent()
        )
        assertEquals(1, result.channels.size)
        val channel = result.channels[0]
        assertEquals("bbc.one.uk", channel.id)
        assertEquals(listOf("BBC One", "BBC 1"), channel.displayNames)
        assertEquals("http://example.com/bbc.png", channel.iconUrl)
    }

    @Test
    fun `parses a programme with title and description`() {
        val result = parse(
            """
            <tv>
              <programme channel="bbc.one.uk" start="20240115120000 +0000" stop="20240115130000 +0000">
                <title>News at Noon</title>
                <desc>Live news coverage.</desc>
              </programme>
            </tv>
            """.trimIndent()
        )
        assertEquals(1, result.programmes.size)
        val programme = result.programmes[0]
        assertEquals("bbc.one.uk", programme.channelId)
        assertEquals("News at Noon", programme.title)
        assertEquals("Live news coverage.", programme.description)
    }

    @Test
    fun `programme with no stop time falls back to its start time`() {
        val result = parse(
            """
            <tv>
              <programme channel="ch1" start="20240115120000 +0000">
                <title>Untimed</title>
              </programme>
            </tv>
            """.trimIndent()
        )
        val programme = result.programmes[0]
        assertEquals(programme.startMillis, programme.stopMillis)
    }

    @Test
    fun `a programme with no start time is skipped`() {
        val result = parse(
            """
            <tv>
              <programme channel="ch1">
                <title>No start</title>
              </programme>
            </tv>
            """.trimIndent()
        )
        assertEquals(0, result.programmes.size)
    }

    @Test
    fun `does not fail and ignores unresolvable text from external entities`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE tv [
              <!ENTITY xxe SYSTEM "file:///this/should/not/be/read">
            ]>
            <tv>
              <channel id="ch1">
                <display-name>Safe &xxe; Name</display-name>
              </channel>
            </tv>
        """.trimIndent()
        val result = parse(xml)
        assertEquals(1, result.channels.size)
        assertFalse(result.channels[0].displayNames[0].contains("/this/should/not/be/read"))
    }

    @Test
    fun `a DOCTYPE without external entities parses normally`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE tv SYSTEM "xmltv.dtd">
            <tv>
              <channel id="ch1">
                <display-name>Channel One</display-name>
              </channel>
            </tv>
        """.trimIndent()
        val result = parse(xml)
        assertEquals(1, result.channels.size)
        assertEquals("Channel One", result.channels[0].displayNames[0])
    }

    @Test
    fun `truncates text content longer than the 16KB limit`() {
        val longTitle = "x".repeat(XmlTvParser.MAX_TEXT_LENGTH + 5000)
        val xml = """
            <tv>
              <programme channel="ch1" start="20240115120000 +0000">
                <title>$longTitle</title>
              </programme>
            </tv>
        """.trimIndent()
        val result = parse(xml)
        assertTrue(result.programmes[0].title.length <= XmlTvParser.MAX_TEXT_LENGTH)
    }

    @Test
    fun `a channel with no id is not collected`() {
        val result = parse(
            """
            <tv>
              <channel>
                <display-name>No Id</display-name>
              </channel>
            </tv>
            """.trimIndent()
        )
        assertEquals(0, result.channels.size)
    }

    @Test
    fun `empty document with no channels or programmes parses cleanly`() {
        val result = parse("<tv></tv>")
        assertTrue(result.channels.isEmpty())
        assertTrue(result.programmes.isEmpty())
        assertFalse(result.channelLimitExceeded)
        assertFalse(result.programmeLimitExceeded)
    }

    @Test
    fun `programme with no description leaves it null`() {
        val result = parse(
            """
            <tv>
              <programme channel="ch1" start="20240115120000 +0000">
                <title>No desc</title>
              </programme>
            </tv>
            """.trimIndent()
        )
        assertNull(result.programmes[0].description)
    }
}
