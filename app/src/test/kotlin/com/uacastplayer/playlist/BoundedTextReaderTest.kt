package com.uacastplayer.playlist

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedTextReaderTest {

    @Test
    fun `reads text fully when under the limit`() {
        val text = "hello world"
        val result = BoundedTextReader.readText(ByteArrayInputStream(text.toByteArray()), maxBytes = 1024)
        assertEquals(BoundedReadResult.Success(text), result)
    }

    @Test
    fun `reads text that lands exactly on the limit`() {
        val text = "a".repeat(10)
        val result = BoundedTextReader.readText(ByteArrayInputStream(text.toByteArray()), maxBytes = 10)
        assertEquals(BoundedReadResult.Success(text), result)
    }

    @Test
    fun `reports size limit exceeded for content one byte over the limit`() {
        val text = "a".repeat(11)
        val result = BoundedTextReader.readText(ByteArrayInputStream(text.toByteArray()), maxBytes = 10)
        assertTrue(result is BoundedReadResult.SizeLimitExceeded)
    }

    @Test
    fun `reports size limit exceeded for content spanning many internal chunks`() {
        val text = "x".repeat(50_000)
        val result = BoundedTextReader.readText(ByteArrayInputStream(text.toByteArray()), maxBytes = 8_000_000)
        assertEquals(BoundedReadResult.Success(text), result)

        val tooBig = "x".repeat(20_000)
        val limited = BoundedTextReader.readText(ByteArrayInputStream(tooBig.toByteArray()), maxBytes = 10_000)
        assertTrue(limited is BoundedReadResult.SizeLimitExceeded)
    }

    @Test
    fun `empty stream yields empty text`() {
        val result = BoundedTextReader.readText(ByteArrayInputStream(ByteArray(0)), maxBytes = 10)
        assertEquals(BoundedReadResult.Success(""), result)
    }
}
