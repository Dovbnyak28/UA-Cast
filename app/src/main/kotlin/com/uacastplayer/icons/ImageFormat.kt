package com.uacastplayer.icons

enum class ImageFormat { PNG, JPEG, WEBP, GIF, SVG, ICO, BMP }

/**
 * Validates icon bytes by magic number rather than trusting a URL's extension or a server's
 * Content-Type header - both are easy for a misbehaving/hostile CDN to get wrong or lie about.
 */
object ImageFormatDetector {

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val gif87 = "GIF87a".toByteArray(Charsets.US_ASCII)
    private val gif89 = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val bmp = "BM".toByteArray(Charsets.US_ASCII)
    private val ico = byteArrayOf(0x00, 0x00, 0x01, 0x00)
    private val riff = "RIFF".toByteArray(Charsets.US_ASCII)
    private val webp = "WEBP".toByteArray(Charsets.US_ASCII)

    private const val SVG_SNIFF_WINDOW = 512

    fun detect(bytes: ByteArray): ImageFormat? = when {
        bytes.startsWith(png) -> ImageFormat.PNG
        bytes.startsWith(jpeg) -> ImageFormat.JPEG
        bytes.startsWith(gif87) || bytes.startsWith(gif89) -> ImageFormat.GIF
        bytes.startsWith(bmp) -> ImageFormat.BMP
        bytes.startsWith(ico) -> ImageFormat.ICO
        bytes.size >= 12 && bytes.startsWith(riff) && regionMatches(bytes, 8, webp) -> ImageFormat.WEBP
        looksLikeSvg(bytes) -> ImageFormat.SVG
        else -> null
    }

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        val prefix = String(bytes, 0, minOf(bytes.size, SVG_SNIFF_WINDOW), Charsets.UTF_8)
        return prefix.contains("<svg", ignoreCase = true)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = regionMatches(this, 0, prefix)

    private fun regionMatches(bytes: ByteArray, offset: Int, needle: ByteArray): Boolean {
        if (bytes.size < offset + needle.size) return false
        for (i in needle.indices) {
            if (bytes[offset + i] != needle[i]) return false
        }
        return true
    }
}
