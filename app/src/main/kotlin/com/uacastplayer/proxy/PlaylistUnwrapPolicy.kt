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

    /** The wrapped stream URL - resolved against [baseUrl] - or null when [playlistText] is a
     * real media/master playlist that should go through the normal rewrite path. A wrapper with
     * SEVERAL stream URLs (a main + backup source list, confirmed in the field by the receiver
     * spinning up one remux per entry) unwraps to the FIRST one - the same pick an ordinary
     * player makes; the rest are alternates for manual failover, not simultaneous streams. All
     * entries must be non-playlist URLs for the wrapper reading to apply at all. */
    fun unwrapTarget(playlistText: String, baseUrl: String): String? {
        val lines = playlistText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val parsed = HlsMediaPlaylistParser.parse(playlistText)
        if (!parsed.hasPlaylistHeader) return null

        // Keep the raw tag-presence check for a malformed TARGETDURATION: it is still evidence of
        // a media playlist and must not turn its first segment into an endless wrapper stream.
        val isRealPlaylist = parsed.isMaster || lines.any { it.startsWith("#EXT-X-TARGETDURATION") }
        val uris = parsed.segmentUris
        val isWrapper = !isRealPlaylist && uris.isNotEmpty() && uris.none { looksLikePlaylistUrl(it) }
        return if (isWrapper) M3u8Rewriter.resolveUrl(baseUrl, uris.first()) else null
    }

    private fun looksLikePlaylistUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }
}
