package com.uacastplayer.core.io

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedByteReaderTest {

    @Test
    fun `reads bytes fully when under the limit`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val result = BoundedByteReader.readBytes(ByteArrayInputStream(bytes), maxBytes = 1024)
        check(result is BoundedBytesResult.Success)
        assertArrayEquals(bytes, result.bytes)
    }

    @Test
    fun `reports size limit exceeded for content over the limit`() {
        val bytes = ByteArray(20) { it.toByte() }
        val result = BoundedByteReader.readBytes(ByteArrayInputStream(bytes), maxBytes = 10)
        assertTrue(result is BoundedBytesResult.SizeLimitExceeded)
    }

    @Test
    fun `reads content that lands exactly on the limit`() {
        val bytes = ByteArray(10) { it.toByte() }
        val result = BoundedByteReader.readBytes(ByteArrayInputStream(bytes), maxBytes = 10)
        check(result is BoundedBytesResult.Success)
        assertArrayEquals(bytes, result.bytes)
    }

    @Test
    fun `empty stream yields empty bytes`() {
        val result = BoundedByteReader.readBytes(ByteArrayInputStream(ByteArray(0)), maxBytes = 10)
        check(result is BoundedBytesResult.Success)
        assertArrayEquals(ByteArray(0), result.bytes)
    }
}
