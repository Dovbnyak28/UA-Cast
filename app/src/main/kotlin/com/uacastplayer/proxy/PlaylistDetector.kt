package com.uacastplayer.proxy

/**
 * Decides whether an origin response is an HLS/M3U playlist from what it actually sent back -
 * a URL's file extension is, at best, a hint (tokenized/extensionless IPTV URLs are common) and
 * at worst actively misleading, so it must never be the deciding signal for how a response gets
 * served (rewritten as a playlist vs. passed through byte-for-byte).
 */
object PlaylistDetector {

    // Covers application/x-mpegURL, application/vnd.apple.mpegurl, audio/mpegurl, etc. in one go.
    private const val PLAYLIST_CONTENT_TYPE_MARKER = "mpegurl"
    private const val UTF8_BOM_FIRST_BYTE = 0xEF
    private const val UTF8_BOM_SECOND_BYTE = 0xBB
    private const val UTF8_BOM_THIRD_BYTE = 0xBF
    private val PLAYLIST_MAGIC = "#EXTM3U".map { it.code.toByte() }
    private val UTF8_BOM = listOf(
        UTF8_BOM_FIRST_BYTE,
        UTF8_BOM_SECOND_BYTE,
        UTF8_BOM_THIRD_BYTE,
    ).map { it.toByte() }

    fun isPlaylist(contentType: String?, bodyPrefix: ByteArray): Boolean {
        if (contentType?.lowercase()?.contains(PLAYLIST_CONTENT_TYPE_MARKER) == true) return true
        return startsWithPlaylistMagic(bodyPrefix)
    }

    private fun startsWithPlaylistMagic(bodyPrefix: ByteArray): Boolean {
        val offset = if (startsWith(bodyPrefix, UTF8_BOM, 0)) UTF8_BOM.size else 0
        return startsWith(bodyPrefix, PLAYLIST_MAGIC, offset)
    }

    private fun startsWith(bytes: ByteArray, prefix: List<Byte>, offset: Int): Boolean {
        return bytes.size - offset >= prefix.size && prefix.indices.all { index ->
            bytes[offset + index] == prefix[index]
        }
    }
}
