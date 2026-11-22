package app.zorik.player.backup

import app.zorik.player.core.settings.IconDisplayMode
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.core.settings.BufferSize
import app.zorik.player.playlist.PlaylistSource
import app.zorik.player.playlist.PlaylistSourceType
import app.zorik.player.favorites.FavoriteChannel

/** Counts only; URLs, credentials and embedded playlists never enter the preview UI. */
data class BackupPreview(
    val sourceCount: Int,
    val favoriteCount: Int,
    val localPlaylistCount: Int,
    val changedSettings: List<String>,
    val skippedSourceCount: Int = 0,
)

object BackupPreviewPolicy {
    fun create(imported: BackupData, current: BackupData): BackupPreview {
        val existingSources = current.sources.mapNotNull { source ->
            PlaylistSourceType.entries.firstOrNull { it.name == source.type }?.let { type ->
                PlaylistSource(source.id, type, source.location, source.displayName, source.addedAtEpochMillis)
            }
        }
        val favorites = current.favorites.map {
            FavoriteChannel(it.key, it.displayName, it.streamUrl, it.tvgId, it.groupTitle, it.addedAtMillis)
        }
        val merge = BackupMergePolicy.merge(existingSources, favorites, imported.sources, imported.favorites)
        val mergedLocations = merge.sources.mapTo(HashSet()) { it.location }
        val changes = listOf(
            "iconDisplayMode" to (imported.settings.iconDisplayMode to current.settings.iconDisplayMode),
            "listDensity" to (imported.settings.listDensity to current.settings.listDensity),
            "bufferSize" to (imported.settings.bufferSize to current.settings.bufferSize),
        ).filter { (key, values) ->
            values.first != values.second && validSetting(key, values.first)
        }.map { it.first }.toMutableList()
        BackupEpgPolicy.changedSetting(imported.settings, current.settings)?.let(changes::add)
        return BackupPreview(
            merge.importedSourceCount,
            merge.importedFavoriteCount,
            imported.sources.count { source ->
                source.playlistBase64 != null && source.location in mergedLocations
            },
            changes,
            merge.sourceLimitExceededCount,
        )
    }

    private fun validSetting(key: String, value: String?): Boolean = when (key) {
        "iconDisplayMode" -> IconDisplayMode.entries.any { it.name == value }
        "listDensity" -> ListDensity.entries.any { it.name == value }
        "bufferSize" -> BufferSize.entries.any { it.name == value }
        else -> false
    }
}
