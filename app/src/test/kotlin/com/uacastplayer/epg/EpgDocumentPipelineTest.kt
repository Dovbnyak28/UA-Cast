package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgDocumentPipelineTest {

    private val now = Instant.parse("2026-08-25T12:00:00Z").toEpochMilli()

    @Test
    fun `plain and gzip documents run through the same production pipeline`() {
        val plain = xml().toByteArray()
        val zipped = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(plain) }
        }.toByteArray()

        val plainData = parse(plain)
        val zippedData = parse(zipped)

        assertEquals(plainData.index.channels, zippedData.index.channels)
        assertEquals(plainData.programmesByChannelId, zippedData.programmesByChannelId)
        assertEquals(listOf("Early", "Late"), plainData.programmesByChannelId.getValue("one").map { it.title })
    }

    @Test
    fun `production retention drops past and unreachable future programmes`() {
        val data = parse(
            xml(
                programmes = listOf(
                    programme("20260824080000 +0000", "20260824090000 +0000", "Past"),
                    programme("20260825130000 +0000", "20260825140000 +0000", "Current day"),
                    programme("20260830080000 +0000", "20260830090000 +0000", "Too far"),
                ).joinToString(""),
            ).toByteArray(),
        )

        assertEquals(listOf("Current day"), data.programmesByChannelId.getValue("one").map { it.title })
        assertFalse(data.truncation.any)
    }

    @Test
    fun `heap budget is applied before index construction`() {
        val programmes = List(HEAP_BUDGET_FLOOR_PLUS_ONE) { index ->
            programme(
                start = "20260825120000 +0000",
                stop = "20260825123000 +0000",
                title = "P$index",
            )
        }.joinToString("")

        val data = EpgDocumentPipeline.parse(
            rawInput = ByteArrayInputStream(xml(programmes).toByteArray()),
            nowMillis = now,
            zoneId = ZoneOffset.UTC,
            maxHeapBytes = 1L,
        )

        assertEquals(40_000, data.programmesByChannelId.getValue("one").size)
        assertTrue(data.truncation.programmesDropped)
    }

    private fun parse(bytes: ByteArray) = EpgDocumentPipeline.parse(
        rawInput = ByteArrayInputStream(bytes),
        nowMillis = now,
        zoneId = ZoneOffset.UTC,
        maxHeapBytes = Long.MAX_VALUE,
    )

    private fun xml(programmes: String = defaultProgrammes()) = """
        <tv>
          <channel id="one"><display-name>One</display-name></channel>
          $programmes
        </tv>
    """.trimIndent()

    private fun defaultProgrammes() =
        programme("20260825130000 +0000", "20260825140000 +0000", "Late") +
            programme("20260825120000 +0000", "20260825130000 +0000", "Early")

    private fun programme(start: String, stop: String, title: String) =
        "<programme start=\"$start\" stop=\"$stop\" channel=\"one\"><title>$title</title></programme>"

    private companion object {
        const val HEAP_BUDGET_FLOOR_PLUS_ONE = 40_001
    }
}
