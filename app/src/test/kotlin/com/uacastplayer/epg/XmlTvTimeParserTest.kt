package com.uacastplayer.epg

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmlTvTimeParserTest {

    private fun expected(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, offsetHours: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.ofHours(offsetHours))
            .toInstant()
            .toEpochMilli()

    @Test
    fun `parses UTC timestamp with zero offset`() {
        val result = XmlTvTimeParser.parse("20240115120000 +0000")
        assertEquals(expected(2024, 1, 15, 12, 0, 0, 0), result)
    }

    @Test
    fun `parses positive offset`() {
        val result = XmlTvTimeParser.parse("20240115120000 +0300")
        assertEquals(expected(2024, 1, 15, 12, 0, 0, 3), result)
    }

    @Test
    fun `parses negative offset`() {
        val result = XmlTvTimeParser.parse("20240115120000 -0500")
        assertEquals(expected(2024, 1, 15, 12, 0, 0, -5), result)
    }

    @Test
    fun `parses without a space before the offset`() {
        val result = XmlTvTimeParser.parse("20240115120000+0200")
        assertEquals(expected(2024, 1, 15, 12, 0, 0, 2), result)
    }

    @Test
    fun `missing offset defaults to UTC`() {
        val result = XmlTvTimeParser.parse("20240115120000")
        assertEquals(expected(2024, 1, 15, 12, 0, 0, 0), result)
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
}
