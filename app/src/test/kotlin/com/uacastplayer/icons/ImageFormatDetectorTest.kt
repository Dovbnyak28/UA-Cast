package com.uacastplayer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `detects AVIF with a major brand`() {
        val bytes = byteArrayOf(
            0, 0, 0, 0,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(),
            0, 0, 0, 0,
        )
        assertEquals(ImageFormat.AVIF, ImageFormatDetector.detect(bytes))
    }

    @Test
    fun `detects AVIF when the compatible brand carries the format`() {
        val bytes = byteArrayOf(
            0, 0, 0, 0,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), '1'.code.toByte(),
            0, 0, 0, 0,
            'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(),
        )
        assertEquals(ImageFormat.AVIF, ImageFormatDetector.detect(bytes))
    }

    @Test
    fun `does not treat an unrelated ISO-BMFF brand as AVIF`() {
        val bytes = byteArrayOf(
            0, 0, 0, 0,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte(),
            0, 0, 0, 0,
        )
        assertNull(ImageFormatDetector.detect(bytes))
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

    // Regression guard: SVG icons only render at all because SvgDecoder is registered in
    // UaCastPlayerApp's ImageLoader specifically for the ImageFormat.SVG that IconDiskCache
    // already accepts here. If SVG support is ever "fixed" by dropping it from this detector
    // instead of keeping the decoder wired up, disk-cached SVG icons would start being rejected
    // as unrecognized bytes on their very next write, silently regressing the icons those
    // channels used to show.
    @Test
    fun `SVG bytes are recognized, not rejected as an unsupported format`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"></svg>""".toByteArray()
        val format = ImageFormatDetector.detect(svg)
        assertEquals(ImageFormat.SVG, format)
        assertNotEquals(null, format)
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
