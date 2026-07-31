package com.uacastplayer.playlist

/** A saved playlist source the user can switch back to - see PlaylistSourceStore. */
enum class PlaylistSourceType { URL, FILE, XTREAM }

/**
 * One saved playlist source. [id] is the same fingerprint used elsewhere as a channel-list's
 * stable non-reversible id (see PlaylistUiState.activePlaylistId) - deriving it from [location]
 * means adding the same URL/file twice naturally upserts instead of duplicating. [location] is a
 * URL for [PlaylistSourceType.URL]/[PlaylistSourceType.XTREAM] (Xtream's built m3u_plus URL - see
 * XtreamUrlBuilder) or a content:// URI string for [PlaylistSourceType.FILE].
 */
data class PlaylistSource(
    val id: String,
    val type: PlaylistSourceType,
    val location: String,
    val displayName: String?,
    val addedAtEpochMillis: Long,
)
