package com.uacastplayer.icons

enum class ImageFormat { PNG, JPEG, WEBP, GIF, SVG, ICO, BMP, AVIF }

/**
 * Validates icon bytes by magic number rather than trusting a URL's extension or a server's
 * Content-Type header - both are easy for a misbehaving/hostile CDN to get wrong or lie about.
 */
object ImageFormatDetector {

    private const val PNG_LEADING_BYTE = 0x89
    private const val PNG_SUBSTITUTE_BYTE = 0x1A
    private const val JPEG_MARKER_BYTE = 0xFF
    private const val JPEG_START_OF_IMAGE_BYTE = 0xD8
    private const val ICO_IMAGE_TYPE_BYTE = 0x01
    private const val WEBP_MIN_HEADER_BYTES = 12
    private const val WEBP_FORMAT_OFFSET = 8
    private const val AVIF_MIN_HEADER_BYTES = 16
    private const val AVIF_FTYP_OFFSET = 4
    private const val AVIF_MAJOR_BRAND_OFFSET = 8
    private const val AVIF_COMPATIBLE_BRANDS_OFFSET = 16

    private val png = byteArrayOf(
        PNG_LEADING_BYTE.toByte(),
        'P'.code.toByte(),
        'N'.code.toByte(),
        'G'.code.toByte(),
        '\r'.code.toByte(),
        '\n'.code.toByte(),
        PNG_SUBSTITUTE_BYTE.toByte(),
        '\n'.code.toByte(),
    )
    private val jpeg = byteArrayOf(
        JPEG_MARKER_BYTE.toByte(),
        JPEG_START_OF_IMAGE_BYTE.toByte(),
        JPEG_MARKER_BYTE.toByte(),
    )
    private val gif87 = "GIF87a".toByteArray(Charsets.US_ASCII)
    private val gif89 = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val bmp = "BM".toByteArray(Charsets.US_ASCII)
    private val ico = byteArrayOf(0x00, 0x00, ICO_IMAGE_TYPE_BYTE.toByte(), 0x00)
    private val riff = "RIFF".toByteArray(Charsets.US_ASCII)
    private val webp = "WEBP".toByteArray(Charsets.US_ASCII)
    private val ftyp = "ftyp".toByteArray(Charsets.US_ASCII)
    private val avifBrands = setOf("avif", "avis")

    private const val SVG_SNIFF_WINDOW = 512

    fun detect(bytes: ByteArray): ImageFormat? = when {
        bytes.startsWith(png) -> ImageFormat.PNG
        bytes.startsWith(jpeg) -> ImageFormat.JPEG
        bytes.startsWith(gif87) || bytes.startsWith(gif89) -> ImageFormat.GIF
        bytes.startsWith(bmp) -> ImageFormat.BMP
        bytes.startsWith(ico) -> ImageFormat.ICO
        bytes.size >= WEBP_MIN_HEADER_BYTES &&
            bytes.startsWith(riff) &&
            regionMatches(bytes, WEBP_FORMAT_OFFSET, webp) -> ImageFormat.WEBP
        isAvif(bytes) -> ImageFormat.AVIF
        looksLikeSvg(bytes) -> ImageFormat.SVG
        else -> null
    }

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        val prefix = String(bytes, 0, minOf(bytes.size, SVG_SNIFF_WINDOW), Charsets.UTF_8)
        return prefix.contains("<svg", ignoreCase = true)
    }

    /** AVIF is an ISO-BMFF container: `....ftyp` followed by an avif/avis brand. */
    private fun isAvif(bytes: ByteArray): Boolean {
        val hasHeader = bytes.size >= AVIF_MIN_HEADER_BYTES &&
            regionMatches(bytes, AVIF_FTYP_OFFSET, ftyp)
        var offset = AVIF_MAJOR_BRAND_OFFSET
        var foundBrand = false
        while (hasHeader && offset + BRAND_LENGTH <= bytes.size && offset < MAX_FTYP_SCAN_BYTES) {
            if (brandAt(bytes, offset) in avifBrands) {
                foundBrand = true
                break
            }
            offset += BRAND_LENGTH
        }
        return foundBrand
    }

    private fun brandAt(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, BRAND_LENGTH, Charsets.US_ASCII)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = regionMatches(this, 0, prefix)

    private fun regionMatches(bytes: ByteArray, offset: Int, needle: ByteArray): Boolean {
        return bytes.size >= offset + needle.size && needle.indices.all { index ->
            bytes[offset + index] == needle[index]
        }
    }

    private const val BRAND_LENGTH = 4
    private const val MAX_FTYP_SCAN_BYTES = 256
}
