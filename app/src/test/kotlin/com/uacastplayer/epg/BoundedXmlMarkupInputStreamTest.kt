package com.uacastplayer.epg

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedXmlMarkupInputStreamTest {

    @Test
    fun `rejects a huge start tag before the whole attribute is read`() {
        val source = ByteArrayInputStream(oversizedChannelTag().toByteArray(Charsets.UTF_8))
        val guarded = BoundedXmlMarkupInputStream.create(source, MAX_MARKUP_BYTES)

        val failure = runCatching { guarded.readBytes() }.exceptionOrNull()

        assertTrue("Expected an IOException, got $failure", failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("XML markup exceeds"))
        assertTrue("The guard should stop the stream before the large value is consumed", source.available() > 0)
    }

    @Test
    fun `recognizes oversized tags in UTF-16 encodings`() {
        listOf(Charsets.UTF_16, Charsets.UTF_16BE, Charsets.UTF_16LE).forEach { charset ->
            val source = ByteArrayInputStream(oversizedChannelTag().toByteArray(charset))
            val guarded = BoundedXmlMarkupInputStream.create(source, MAX_MARKUP_BYTES)

            val failure = runCatching { guarded.readBytes() }.exceptionOrNull()

            assertTrue("UTF-16 encoding $charset was not guarded: $failure", failure is IOException)
            assertTrue(failure?.message.orEmpty().contains("XML markup exceeds"))
        }
    }

    @Test
    fun `skip cannot bypass the markup-size limit`() {
        val source = ByteArrayInputStream(oversizedChannelTag().toByteArray(Charsets.UTF_8))
        val guarded = BoundedXmlMarkupInputStream.create(source, MAX_MARKUP_BYTES)

        val failure = runCatching { guarded.skip(Long.MAX_VALUE) }.exceptionOrNull()

        assertTrue("Expected an IOException, got $failure", failure is IOException)
        assertTrue(failure?.message.orEmpty().contains("XML markup exceeds"))
    }

    @Test
    fun `checks cancellation while streaming large comments`() {
        val xml = "<!--${"x".repeat(MAX_MARKUP_BYTES * 2)}-->"
        var checks = 0
        val guarded = BoundedXmlMarkupInputStream.create(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            MAX_MARKUP_BYTES,
        ) {
            checks++
            throw CancellationException("cancelled while reading XML")
        }

        val failure = runCatching { guarded.readBytes() }.exceptionOrNull()

        assertTrue("Expected cancellation, got $failure", failure is CancellationException)
        assertEquals(1, checks)
    }

    @Test
    fun `large comments containing tag-like text remain streaming and unchanged`() {
        val xml = "<!-- <channel fake=\"${"x".repeat(MAX_MARKUP_BYTES * 2)}\"> -->\n<tv/>"
        val bytes = xml.toByteArray(Charsets.UTF_8)
        val guarded = BoundedXmlMarkupInputStream.create(ByteArrayInputStream(bytes), MAX_MARKUP_BYTES)

        assertArrayEquals(bytes, guarded.readBytes())
    }

    private fun oversizedChannelTag(): String =
        "<channel id=\"${"x".repeat(MAX_MARKUP_BYTES * 8)}\"/>"

    private companion object {
        const val MAX_MARKUP_BYTES = 16 * 1024
    }
}
