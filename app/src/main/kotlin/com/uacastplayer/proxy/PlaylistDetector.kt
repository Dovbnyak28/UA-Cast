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
    private val PLAYLIST_MAGIC = "#EXTM3U".map { it.code.toByte() }
    private val UTF8_BOM = listOf(0xEF, 0xBB, 0xBF).map { it.toByte() }

    fun isPlaylist(contentType: String?, bodyPrefix: ByteArray): Boolean {
        if (contentType?.lowercase()?.contains(PLAYLIST_CONTENT_TYPE_MARKER) == true) return true
        return startsWithPlaylistMagic(bodyPrefix)
    }

    private fun startsWithPlaylistMagic(bodyPrefix: ByteArray): Boolean {
        val offset = if (startsWith(bodyPrefix, UTF8_BOM, 0)) UTF8_BOM.size else 0
        return startsWith(bodyPrefix, PLAYLIST_MAGIC, offset)
    }

    private fun startsWith(bytes: ByteArray, prefix: List<Byte>, offset: Int): Boolean {
        if (bytes.size - offset < prefix.size) return false
        for (i in prefix.indices) {
            if (bytes[offset + i] != prefix[i]) return false
        }
        return true
    }
}
