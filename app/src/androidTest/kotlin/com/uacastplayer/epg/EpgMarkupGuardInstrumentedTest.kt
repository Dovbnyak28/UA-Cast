package com.uacastplayer.epg

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production XML guard with Android's platform SAX implementation on ART. */
@RunWith(AndroidJUnit4::class)
class EpgMarkupGuardInstrumentedTest {

    @Test
    fun oversizedCompressedAttributeIsRejectedBeforePlatformSaxRetainsIt() {
        val xml = "<tv><channel id=\"${"x".repeat(EpgDocumentPipeline.MAX_XML_MARKUP_BYTES * 8)}\"/></tv>"
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(xml.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        val failure = assertThrows(Exception::class.java) {
            EpgDocumentPipeline.parse(
                rawInput = ByteArrayInputStream(compressed),
                nowMillis = 0L,
                zoneId = ZoneOffset.UTC,
                maxHeapBytes = Runtime.getRuntime().maxMemory(),
            )
        }

        assertTrue(
            generateSequence(failure as Throwable?) { it.cause }.any {
                it is IOException && it.message.orEmpty().contains("XML markup exceeds")
            },
        )
    }

    @Test
    fun validUtf16XmlParsesThroughTheGuardOnPlatformSax() {
        val xml = "<tv><channel id=\"one\"><display-name>News</display-name></channel></tv>"
        val parsed = EpgDocumentPipeline.parse(
            rawInput = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_16)),
            nowMillis = 0L,
            zoneId = ZoneOffset.UTC,
            maxHeapBytes = Runtime.getRuntime().maxMemory(),
        )

        assertEquals(listOf("News"), parsed.index.channels.single().displayNames)
    }

    @Test
    fun cancellationIsObservedWhilePlatformSaxSkipsALargeComment() {
        val xml = "<tv><!--${"x".repeat(EpgDocumentPipeline.MAX_XML_MARKUP_BYTES * 4)}--></tv>"
        var checks = 0

        val failure = assertThrows(Exception::class.java) {
            EpgDocumentPipeline.parse(
                rawInput = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
                nowMillis = 0L,
                zoneId = ZoneOffset.UTC,
                maxHeapBytes = Runtime.getRuntime().maxMemory(),
                checkCancellation = {
                    checks++
                    if (checks == 2) throw CancellationException("cancel during XML stream")
                },
            )
        }

        assertTrue(
            generateSequence(failure as Throwable?) { it.cause }.any { it is CancellationException },
        )
        assertEquals(2, checks)
    }
}
