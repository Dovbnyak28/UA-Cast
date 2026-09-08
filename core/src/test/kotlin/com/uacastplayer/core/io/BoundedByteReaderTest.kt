package com.uacastplayer.core.io

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundedByteReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun `copyToFile writes the full stream when under the limit`() {
        val bytes = ByteArray(20_000) { it.toByte() }
        val destination = tempFolder.newFile()

        val result = BoundedByteReader.copyToFile(ByteArrayInputStream(bytes), destination, maxBytes = 1024 * 1024)

        check(result is BoundedFileCopyResult.Success)
        assertEquals(bytes.size.toLong(), result.bytesWritten)
        assertArrayEquals(bytes, destination.readBytes())
    }

    @Test
    fun `copyToFile reports size limit exceeded for content over the limit`() {
        val bytes = ByteArray(20) { it.toByte() }
        val destination = tempFolder.newFile()

        val result = BoundedByteReader.copyToFile(ByteArrayInputStream(bytes), destination, maxBytes = 10)

        assertTrue(result is BoundedFileCopyResult.SizeLimitExceeded)
    }

    @Test
    fun `copyToFile writes content that lands exactly on the limit`() {
        val bytes = ByteArray(10) { it.toByte() }
        val destination = tempFolder.newFile()

        val result = BoundedByteReader.copyToFile(ByteArrayInputStream(bytes), destination, maxBytes = 10)

        check(result is BoundedFileCopyResult.Success)
        assertArrayEquals(bytes, destination.readBytes())
    }

    @Test
    fun `readAtMostBytes truncates instead of failing when the source keeps sending data`() {
        val bytes = ByteArray(1000) { it.toByte() }

        val result = BoundedByteReader.readAtMostBytes(ByteArrayInputStream(bytes), maxBytes = 128)

        assertEquals(128, result.size)
        assertArrayEquals(bytes.copyOf(128), result)
    }

    @Test
    fun `readAtMostBytes returns the full content when under the cap`() {
        val bytes = byteArrayOf(1, 2, 3)

        val result = BoundedByteReader.readAtMostBytes(ByteArrayInputStream(bytes), maxBytes = 128)

        assertArrayEquals(bytes, result)
    }
}
