package app.zorik.player.app

import app.zorik.player.backup.BackupSettings
import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.playlist.PlaylistSource

/** Existing owners remain the only writers of sources, favorites and settings. */
internal data class BackupRestoreTarget(
    val currentSources: () -> List<PlaylistSource>,
    val currentFavorites: () -> List<FavoriteChannel>,
    val onSourcesMerged: suspend (List<PlaylistSource>) -> Unit,
    val onSettingsImported: (BackupSettings) -> Unit,
    val awaitSourcesPersisted: suspend () -> Boolean,
)
