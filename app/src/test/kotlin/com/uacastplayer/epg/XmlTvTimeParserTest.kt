package com.uacastplayer.epg

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class XmlTvTimeParserTest {

    private fun expected(localDateTime: LocalDateTime, offsetHours: Int): Long =
        localDateTime.toInstant(ZoneOffset.ofHours(offsetHours)).toEpochMilli()

    @Test
    fun `parses UTC timestamp with zero offset`() {
        val result = XmlTvTimeParser.parse("20240115120000 +0000")
        assertEquals(expected(LocalDateTime.of(2024, 1, 15, 12, 0), 0), result)
    }

    @Test
    fun `parses positive offset`() {
        val result = XmlTvTimeParser.parse("20240115120000 +0300")
        assertEquals(expected(LocalDateTime.of(2024, 1, 15, 12, 0), 3), result)
    }

    @Test
    fun `parses negative offset`() {
        val result = XmlTvTimeParser.parse("20240115120000 -0500")
        assertEquals(expected(LocalDateTime.of(2024, 1, 15, 12, 0), -5), result)
    }

    @Test
    fun `parses without a space before the offset`() {
        val result = XmlTvTimeParser.parse("20240115120000+0200")
        assertEquals(expected(LocalDateTime.of(2024, 1, 15, 12, 0), 2), result)
    }

    @Test
    fun `missing offset defaults to UTC`() {
        val result = XmlTvTimeParser.parse("20240115120000")
        assertEquals(expected(LocalDateTime.of(2024, 1, 15, 12, 0), 0), result)
    }

    @Test
    fun `returns null for a string shorter than 14 characters`() {
        assertNull(XmlTvTimeParser.parse("2024011512"))
    }

    @Test
    fun `returns null for an invalid month`() {
        assertNull(XmlTvTimeParser.parse("20241315120000 +0000"))
    }

    @Test
    fun `returns null for a malformed offset`() {
        assertNull(XmlTvTimeParser.parse("20240115120000 abcd"))
    }

    @Test
    fun `accepts the largest structurally valid offset`() {
        assertNotNull(XmlTvTimeParser.parse("20240115120000 +2359"))
    }

    @Test
    fun `rejects offset hour outside the clock range`() {
        assertNull(XmlTvTimeParser.parse("20240115120000 +2400"))
    }

    @Test
    fun `rejects offset minute outside the clock range`() {
        assertNull(XmlTvTimeParser.parse("20240115120000 -1260"))
    }

    @Test
    fun `rejects offsets with extra or missing digits`() {
        assertNull(XmlTvTimeParser.parse("20240115120000 +02000"))
        assertNull(XmlTvTimeParser.parse("20240115120000 +020"))
    }
}
