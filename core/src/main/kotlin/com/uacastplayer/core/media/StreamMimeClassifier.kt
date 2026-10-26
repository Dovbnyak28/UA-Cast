package com.uacastplayer.core.media

enum class StreamType { HLS, DASH, MPEG_TS, PROGRESSIVE }

/**
 * IPTV providers routinely serve HLS from URLs with no `.m3u8` extension (query-string tokens,
 * bare paths, etc.), so HLS remains the default for ambiguous URLs. Explicit container extensions
 * must not be mislabelled as adaptive manifests.
 * Shared by local playback and Cast content-type selection, with no dependency on either runtime.
 */
object StreamMimeClassifier {

    fun classify(url: String): StreamType {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".mpd") -> StreamType.DASH
            path.endsWith(".ts") || path.endsWith(".m2ts") -> StreamType.MPEG_TS
            progressiveTypes.keys.any(path::endsWith) -> StreamType.PROGRESSIVE
            else -> StreamType.HLS
        }
    }

    fun progressiveMimeType(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return progressiveTypes.entries.firstOrNull { path.endsWith(it.key) }?.value
    }

    private val progressiveTypes = mapOf(
        ".mp4" to "video/mp4", ".m4v" to "video/mp4", ".mkv" to "video/x-matroska",
        ".webm" to "video/webm", ".mp3" to "audio/mpeg", ".aac" to "audio/aac",
        ".flac" to "audio/flac", ".ogg" to "audio/ogg", ".wav" to "audio/wav",
    )
}
