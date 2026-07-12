package com.uacastplayer.playlist

/**
 * The last successfully parsed playlist, kept on disk so the app can show channels immediately
 * on startup instead of waiting on a fresh network fetch. [sourceFingerprint] is a SHA-256 hex
 * digest of the playlist's source (URL or file identifier), never the raw source itself.
 */
data class PlaylistSnapshot(
    val sourceFingerprint: String,
    val savedAtEpochMillis: Long,
    val channels: List<M3uChannel>,
    val skippedLineCount: Int,
)
