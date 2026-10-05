package com.uacastplayer.backup

import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.core.settings.BufferSize
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.favorites.FavoriteChannel

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
        val changes = listOf(
            "iconDisplayMode" to (imported.settings.iconDisplayMode to current.settings.iconDisplayMode),
            "listDensity" to (imported.settings.listDensity to current.settings.listDensity),
            "bufferSize" to (imported.settings.bufferSize to current.settings.bufferSize),
            "epgSourceId" to (imported.settings.epgSourceId to current.settings.epgSourceId),
            "epgCustomUrl" to (imported.settings.epgCustomUrl to current.settings.epgCustomUrl),
        ).filter { (key, values) ->
            values.first != values.second && validSetting(key, values.first, imported.settings)
        }.map { it.first }
        return BackupPreview(
            merge.importedSourceCount,
            merge.importedFavoriteCount,
            imported.sources.count { source ->
                source.playlistBase64 != null && merge.sources.any { it.location == source.location }
            },
            changes,
            merge.sourceLimitExceededCount,
        )
    }

    private fun validSetting(key: String, value: String?, settings: BackupSettings): Boolean = when (key) {
        "iconDisplayMode" -> IconDisplayMode.entries.any { it.name == value }
        "listDensity" -> ListDensity.entries.any { it.name == value }
        "bufferSize" -> BufferSize.entries.any { it.name == value }
        "epgSourceId" -> settings.epgCustomUrl == null && EpgSource.entries.any { it.id == value }
        else -> value != null
    }
}
