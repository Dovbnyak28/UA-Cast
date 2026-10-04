package com.uacastplayer.app

import com.uacastplayer.backup.BackupSettings
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.playlist.PlaylistSource

/** Existing owners remain the only writers of sources, favorites and settings. */
internal data class BackupRestoreTarget(
    val currentSources: () -> List<PlaylistSource>,
    val currentFavorites: () -> List<FavoriteChannel>,
    val onSourcesMerged: suspend (List<PlaylistSource>) -> Unit,
    val onSettingsImported: (BackupSettings) -> Unit,
    val awaitSourcesPersisted: suspend () -> Boolean,
)
