package com.uacastplayer.ui.playlist

/** Which kind of source [AddPlaylistScreen] is currently configured for - just UI state, not
 * persisted anywhere: once a source loads, all three converge on the same URL/file pipeline. */
internal enum class PlaylistSourceType { URL, FILE, XTREAM }
