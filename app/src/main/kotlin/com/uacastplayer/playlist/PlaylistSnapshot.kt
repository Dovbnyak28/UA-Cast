package com.uacastplayer.playlist

/**
 * The last successfully parsed playlist, kept on disk so the app can show channels immediately
 * on startup instead of waiting on a fresh network fetch. [sourceFingerprint] is a SHA-256 hex
 * digest of the playlist's source (URL or file identifier), used as a stable non-reversible id
 * elsewhere (Home's "active playlist" card). [sourceUrl] is the actual URL when the playlist came
 * from one (null for a file import) - stored as plain text, unlike the fingerprint, so a refresh
 * can re-fetch it without the user pasting it in again. This is a deliberate exception to "never
 * the raw source itself": the file lives in app-private storage the same as every other
 * preference/cache this app keeps, and the URL is already handled in the clear on every load.
 */
data class PlaylistSnapshot(
    val sourceFingerprint: String,
    val savedAtEpochMillis: Long,
    val channels: List<M3uChannel>,
    val skippedLineCount: Int,
    val sourceUrl: String? = null,
)
