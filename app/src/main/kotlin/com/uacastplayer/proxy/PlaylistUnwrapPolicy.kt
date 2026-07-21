package com.uacastplayer.proxy

/**
 * Detects the IPTV "wrapper playlist" pattern: an M3U/M3U8 whose entire content is a single
 * reference to an endless raw-TS stream URL - not a real HLS media playlist at all, just a
 * playlist-shaped pointer. Field logs showed why this matters for the proxy: rewriting such a
 * playlist hands the receiver a "segment" URL that is actually an endless raw-TS stream, the proxy
 * then (correctly, per its own rules) remuxes that stream - and returns an HLS *playlist* where
 * the receiver expects MPEG-TS *segment bytes*, so playback silently dies before the first frame.
 * The fix is to unwrap one level: serve the remux playlist AS the top-level playlist resource.
 *
 * The discriminator against a genuine media playlist that momentarily lists a single segment is
 * `#EXT-X-TARGETDURATION`: the HLS spec REQUIRES it in every real media playlist, and wrapper
 * playlists (hand-written `#EXTM3U` + one URL, often with `#EXTINF:-1`) never carry it. A master
 * playlist (`#EXT-X-STREAM-INF`) is never unwrapped either - its references are playlists
 * themselves, which the normal rewrite path already handles.
 */
object PlaylistUnwrapPolicy {

    /** The single wrapped stream URL - resolved against [baseUrl] - or null when [playlistText]
     * is a real media/master playlist that should go through the normal rewrite path. */
    fun unwrapTarget(playlistText: String, baseUrl: String): String? {
        val lines = playlistText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val isRealPlaylist = lines.any {
            it.startsWith("#EXT-X-STREAM-INF") || it.startsWith("#EXT-X-TARGETDURATION")
        }
        val single = lines.filter { !it.startsWith("#") }.singleOrNull()
        val isWrapper = !isRealPlaylist && single != null && !looksLikePlaylistUrl(single)
        return if (isWrapper) M3u8Rewriter.resolveUrl(baseUrl, requireNotNull(single)) else null
    }

    private fun looksLikePlaylistUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }
}
