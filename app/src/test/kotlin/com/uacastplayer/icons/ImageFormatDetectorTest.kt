package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageFormatDetectorTest {

    @Test
    fun `detects PNG`() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        assertEquals(ImageFormat.PNG, ImageFormatDetector.detect(bytes))
    }

    @Test
    fun `detects JPEG`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2)
        assertEquals(ImageFormat.JPEG, ImageFormatDetector.detect(bytes))
    }

    @Test
    fun `detects GIF87a and GIF89a`() {
        assertEquals(ImageFormat.GIF, ImageFormatDetector.detect("GIF87a".toByteArray() + byteArrayOf(1, 2)))
        assertEquals(ImageFormat.GIF, ImageFormatDetector.detect("GIF89a".toByteArray() + byteArrayOf(1, 2)))
    }

    @Test
    fun `detects BMP`() {
        assertEquals(ImageFormat.BMP, ImageFormatDetector.detect("BM".toByteArray() + byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `detects ICO`() {
        val bytes = byteArrayOf(0x00, 0x00, 0x01, 0x00, 1, 2)
        assertEquals(ImageFormat.ICO, ImageFormatDetector.detect(bytes))
    }

    @Test
    fun `detects WEBP inside a RIFF container`() {
        val bytes = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()
        assertEquals(ImageFormat.WEBP, ImageFormatDetector.detect(bytes))
    }

    @Test
    fun `does not detect WEBP for a plain RIFF container such as WAV`() {
        val bytes = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WAVE".toByteArray()
        assertNull(ImageFormatDetector.detect(bytes))
    }

    @Test
    fun `detects SVG by tag sniffing`() {
        val svg = """<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg"></svg>""".toByteArray()
        assertEquals(ImageFormat.SVG, ImageFormatDetector.detect(svg))
    }

    @Test
    fun `returns null for unrecognized content`() {
        assertNull(ImageFormatDetector.detect("<html><body>404</body></html>".toByteArray()))
    }

    @Test
    fun `returns null for an empty byte array`() {
        assertNull(ImageFormatDetector.detect(ByteArray(0)))
    }

    @Test
    fun `returns null for content shorter than any magic number`() {
        assertNull(ImageFormatDetector.detect(byteArrayOf(0x89.toByte(), 0x50)))
    }
}
