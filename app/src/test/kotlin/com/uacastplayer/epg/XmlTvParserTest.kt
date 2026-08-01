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
    fun `parses a programme with a title, discarding the description alongside it`() {
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
    }

    /**
     * `<desc>` is the largest field in a real feed and nothing reads it - see [EpgProgramme]. The
     * risk in skipping it is that its character data leaks into whatever builder happens to be
     * open, so this pins down that a description sitting either side of the title changes nothing.
     */
    @Test
    fun `description text never bleeds into the title, before or after it`() {
        val result = parse(
            """
            <tv>
              <programme channel="ch1" start="20240115120000 +0000">
                <desc>LEADING DESCRIPTION</desc>
                <title>Real Title</title>
                <desc>TRAILING DESCRIPTION</desc>
              </programme>
            </tv>
            """.trimIndent()
        )
        assertEquals("Real Title", result.programmes[0].title)
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
    fun `truncates text content longer than the name-length limit`() {
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

    /**
     * `textTarget.toString()` on the nullable builder resolved to `Any?.toString()`, which yields
     * the literal string "null" - and endElement("title"/"desc") clears that builder
     * unconditionally, so any title/desc nested inside a display-name recorded "null" as the
     * channel's name and put it straight into EpgIndex's name map.
     */
    @Test
    fun `a display-name containing a nested title or desc never yields the literal string null`() {
        val result = parse(
            """
            <tv>
              <channel id="c1"><display-name><desc>nested</desc></display-name></channel>
              <channel id="c2"><display-name><title>nested</title></display-name></channel>
            </tv>
            """.trimIndent()
        )
        assertEquals(emptyList<String>(), result.channels[0].displayNames)
        assertEquals(emptyList<String>(), result.channels[1].displayNames)
    }

    @Test
    fun `an empty or whitespace-only display-name is dropped rather than stored blank`() {
        val result = parse(
            """
            <tv>
              <channel id="c1">
                <display-name></display-name>
                <display-name>   </display-name>
                <display-name>Real Name</display-name>
              </channel>
            </tv>
            """.trimIndent()
        )
        assertEquals(listOf("Real Name"), result.channels[0].displayNames)
    }

    /**
     * Channel ids are pooled so one String is held per distinct id rather than one per programme
     * (up to MAX_PROGRAMMES of them). Identity, not equality, is the whole point of that pooling -
     * assertEquals would pass just as well without it.
     */
    @Test
    fun `programmes on the same channel share one channelId instance`() {
        val result = parse(
            """
            <tv>
              <channel id="ch1"><display-name>One</display-name></channel>
              <programme channel="ch1" start="20240115120000 +0000"><title>A</title></programme>
              <programme channel="ch1" start="20240115130000 +0000"><title>B</title></programme>
              <programme channel="ch1" start="20240115140000 +0000"><title>C</title></programme>
            </tv>
            """.trimIndent()
        )
        assertEquals(3, result.programmes.size)
        val first = result.programmes[0].channelId
        assertTrue(result.programmes.all { it.channelId === first })
        // The <channel> element's own id comes from the same pool.
        assertTrue(result.channels[0].id === first)
    }

    @Test
    fun `distinct channel ids stay distinct through the pool`() {
        val result = parse(
            """
            <tv>
              <programme channel="ch1" start="20240115120000 +0000"><title>A</title></programme>
              <programme channel="ch2" start="20240115130000 +0000"><title>B</title></programme>
            </tv>
            """.trimIndent()
        )
        assertEquals(listOf("ch1", "ch2"), result.programmes.map { it.channelId })
    }
}
