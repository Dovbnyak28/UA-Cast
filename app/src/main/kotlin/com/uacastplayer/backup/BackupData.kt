package com.uacastplayer.backup

/** A saved playlist source, decoupled from `com.uacastplayer.playlist.PlaylistSource` so this
 * package (and its JSON shape) doesn't have to change every time that model does - [type] is
 * PlaylistSourceType's raw enum name. */
data class BackupPlaylistSource(
    val id: String,
    val type: String,
    val location: String,
    val displayName: String?,
    val addedAtEpochMillis: Long,
)

/** Mirrors `com.uacastplayer.favorites.FavoriteChannel` - [streamUrl] is included (despite the
 * name "backup", not just an identifier) because Favorites plays channels straight from its own
 * stored fields rather than re-matching against whatever playlist happens to be active; a
 * favorite without it would show up after import but fail to play. */
data class BackupFavorite(
    val key: String,
    val displayName: String,
    val streamUrl: String,
    val tvgId: String?,
    val groupTitle: String?,
    val addedAtMillis: Long,
)

/**
 * Only settings explicitly worth carrying to another device/install. [epgSourceId] and
 * [epgCustomUrl] are only ever populated when the user chose an EPG source themselves (see
 * AppPreferences.hasChosenEpgSource) - a device-tier default or not-yet-applied suggestion isn't
 * meaningful to replay elsewhere.
 */
data class BackupSettings(
    val iconDisplayMode: String? = null,
    val listDensity: String? = null,
    val bufferSize: String? = null,
    val epgSourceId: String? = null,
    val epgCustomUrl: String? = null,
)

data class BackupData(
    val sources: List<BackupPlaylistSource>,
    val favorites: List<BackupFavorite>,
    val settings: BackupSettings,
)

/** Shown to the user after a successful import (see AppViewModel.importBackupFrom). */
data class BackupImportSummary(val importedSourceCount: Int, val importedFavoriteCount: Int)
