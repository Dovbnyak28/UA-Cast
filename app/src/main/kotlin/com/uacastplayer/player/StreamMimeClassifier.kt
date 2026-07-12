package com.uacastplayer.player

enum class StreamType { HLS, DASH }

/**
 * IPTV providers routinely serve HLS from URLs with no `.m3u8` extension (query-string tokens,
 * bare paths, etc.), so HLS is the default assumption; only an explicit `.mpd` marks DASH.
 */
object StreamMimeClassifier {

    fun classify(url: String): StreamType {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return if (path.endsWith(".mpd")) StreamType.DASH else StreamType.HLS
    }
}
