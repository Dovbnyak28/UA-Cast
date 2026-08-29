package com.uacastplayer.backup

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceAddResult
import com.uacastplayer.playlist.PlaylistSourcePolicy
import com.uacastplayer.playlist.PlaylistSourceType

/**
 * How imported data combines with what's already on the device: favorites are added on top of the
 * existing ones without duplicating (matched by [FavoriteChannel.key], same identity favorites
 * always use); sources are added up to [PlaylistSourcePolicy.MAX_SOURCES] via the same policy a
 * manual add uses, so an import can't silently evict an existing saved source to make room.
 * Settings are a separate, simpler "just overwrite" concern handled by the caller directly through
 * AppViewModel's existing setters - nothing to merge there.
 */
object BackupMergePolicy {
    data class MergeResult(
        val sources: List<PlaylistSource>,
        val favorites: List<FavoriteChannel>,
        val importedSourceCount: Int,
        val importedFavoriteCount: Int,
        val sourceLimitExceededCount: Int,
    )

    fun merge(
        existingSources: List<PlaylistSource>,
        existingFavorites: List<FavoriteChannel>,
        importedSources: List<BackupPlaylistSource>,
        importedFavorites: List<BackupFavorite>,
    ): MergeResult {
        var sources = existingSources
        var importedSourceCount = 0
        var sourceLimitExceededCount = 0
        for (backupSource in importedSources) {
            val type = runCatchingNonFatal { PlaylistSourceType.valueOf(backupSource.type) }.getOrNull() ?: continue
            val candidate = PlaylistSource(
                // The id is redundant portable data, not an authority. Rebuilding it prevents a
                // hand-edited/third-party backup from replacing an unrelated saved source by
                // borrowing its id, and restores PlaylistSource's location-derived invariant.
                id = Fingerprint.of(backupSource.location),
                type = type,
                location = backupSource.location,
                displayName = backupSource.displayName,
                addedAtEpochMillis = backupSource.addedAtEpochMillis,
            )
            when (val result = PlaylistSourcePolicy.add(sources, candidate)) {
                is PlaylistSourceAddResult.Added -> {
                    sources = result.sources
                    importedSourceCount++
                }
                PlaylistSourceAddResult.LimitReached -> sourceLimitExceededCount++
            }
        }

        val existingKeys = existingFavorites.mapTo(mutableSetOf()) { it.key }
        val newFavorites = importedFavorites.mapNotNull { imported ->
            val key = FavoriteKey.of(
                M3uChannel(
                    displayName = imported.displayName,
                    streamUrl = imported.streamUrl,
                    tvgId = imported.tvgId,
                    groupTitle = imported.groupTitle,
                ),
            )
            // Same rule as sources: a serialized key may come from an edited or third-party file.
            // Deriving it from the channel fields keeps toggle/remove identity stable after import.
            if (!existingKeys.add(key)) return@mapNotNull null
            FavoriteChannel(
                key,
                imported.displayName,
                imported.streamUrl,
                imported.tvgId,
                imported.groupTitle,
                imported.addedAtMillis,
            )
        }

        return MergeResult(
            sources = sources,
            favorites = existingFavorites + newFavorites,
            importedSourceCount = importedSourceCount,
            importedFavoriteCount = newFavorites.size,
            sourceLimitExceededCount = sourceLimitExceededCount,
        )
    }
}
