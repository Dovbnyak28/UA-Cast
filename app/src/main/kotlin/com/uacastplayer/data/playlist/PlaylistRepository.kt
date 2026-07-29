package com.uacastplayer.data.playlist

import android.content.Context
import android.net.Uri
import com.uacastplayer.core.net.AppHttp
import com.uacastplayer.core.security.Fingerprint
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uParser
import com.uacastplayer.playlist.PlaylistLoadResult
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.playlist.ChannelGrouper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class PlaylistOutcome {
    data class Loaded(
        val groups: List<GroupedChannels>,
        val skippedLineCount: Int,
        val sourceFingerprint: String? = null,
        /** The URL this came from, so it can be reloaded later without asking the user to paste
         * it in again - null for a file import, since there's nothing to re-fetch. */
        val sourceUrl: String? = null,
        /** EPG URL(s) found in the playlist's own #EXTM3U header (or synthesized for an Xtream
         * source - see XtreamUrlBuilder) - see [com.uacastplayer.epg.EpgSourceAutoDetect]. */
        val epgUrls: List<String> = emptyList(),
    ) : PlaylistOutcome()
    data object SizeLimitExceeded : PlaylistOutcome()
    data class HttpError(val code: Int) : PlaylistOutcome()
    data class ReadError(val message: String?) : PlaylistOutcome()
}

private const val TAG = "PlaylistRepository"

class PlaylistRepository(context: Context) {

    private val appContext = context.applicationContext
    private val httpClient = AppHttp.client(connectTimeoutSeconds = 15, readTimeoutSeconds = 30)
    private val urlLoader = PlaylistUrlLoader(httpClient)
    private val fileLoader = PlaylistFileLoader(appContext)
    private val sourceStore = PlaylistSourceStore(appContext)

    /** [extraEpgUrls] lets an Xtream-built URL (see XtreamUrlBuilder) contribute its synthesized
     * xmltv.php address even when the M3U's own #EXTM3U header doesn't advertise one. */
    suspend fun loadFromUrl(url: String, extraEpgUrls: List<String> = emptyList()): PlaylistOutcome {
        val sourceId = Fingerprint.of(url)
        val outcome = toOutcome(urlLoader.load(url), sourceId, sourceUrl = url, extraEpgUrls = extraEpgUrls)
        persistIfLoaded(sourceId, outcome)
        return outcome
    }

    suspend fun loadFromFile(uri: Uri): PlaylistOutcome {
        val sourceId = Fingerprint.of(uri.toString())
        val outcome = toOutcome(fileLoader.load(uri), sourceId, sourceUrl = null)
        persistIfLoaded(sourceId, outcome)
        return outcome
    }

    /** Cached channels for one saved source (see [PlaylistSource]) - keyed by [sourceId] so
     * switching between multiple saved sources can show the last-known channels immediately
     * instead of always re-fetching over the network. Grouping thousands of channels is CPU work,
     * not disk I/O, so it gets its own [Dispatchers.Default] hop rather than riding along on the
     * [Dispatchers.IO] one used for the snapshot file read. */
    suspend fun restoreSnapshot(sourceId: String): PlaylistOutcome? {
        val snapshot = withContext(Dispatchers.IO) {
            PlaylistSnapshotStore(appContext, sourceId).load()
        } ?: return null
        return withContext(Dispatchers.Default) {
            PlaylistOutcome.Loaded(
                groups = ChannelGrouper.group(snapshot.channels),
                skippedLineCount = snapshot.skippedLineCount,
                sourceFingerprint = snapshot.sourceFingerprint,
                sourceUrl = snapshot.sourceUrl,
            )
        }
    }

    suspend fun loadSources(): List<PlaylistSource> = sourceStore.load()

    suspend fun saveSources(sources: List<PlaylistSource>) = sourceStore.save(sources)

    suspend fun deleteSnapshot(sourceId: String) = withContext(Dispatchers.IO) {
        File(appContext.filesDir, "playlist_snapshot_$sourceId.bin").delete()
        Unit
    }

    /**
     * One-time upgrade path: before multi-playlist support there was exactly one snapshot file
     * (see [LegacyPlaylistSnapshotFile]) and no source list at all. Called only when [loadSources]
     * comes back empty - adopts that single snapshot as the first saved source, migrates its bytes
     * to the new per-source file, and deletes the legacy file so this never runs again. Returns
     * null when there was nothing to migrate (fresh install).
     */
    suspend fun migrateLegacySnapshotIfNeeded(): PlaylistSource? {
        val legacy = LegacyPlaylistSnapshotFile.read(appContext) ?: return null
        val id = legacy.sourceFingerprint.ifBlank { Fingerprint.of(legacy.sourceUrl.orEmpty()) }
        val type = if (legacy.sourceUrl != null) PlaylistSourceType.URL else PlaylistSourceType.FILE
        PlaylistSnapshotStore(appContext, id).save(legacy)
        LegacyPlaylistSnapshotFile.delete(appContext)
        return PlaylistSource(
            id = id,
            type = type,
            location = legacy.sourceUrl.orEmpty(),
            displayName = null,
            addedAtEpochMillis = legacy.savedAtEpochMillis,
        )
    }

    private suspend fun persistIfLoaded(sourceId: String, outcome: PlaylistOutcome) {
        if (outcome !is PlaylistOutcome.Loaded) return
        val channels = outcome.groups.flatMap { it.channels }
        val snapshot = PlaylistSnapshot(
            sourceFingerprint = outcome.sourceFingerprint.orEmpty(),
            savedAtEpochMillis = System.currentTimeMillis(),
            channels = channels,
            skippedLineCount = outcome.skippedLineCount,
            sourceUrl = outcome.sourceUrl,
        )
        try {
            PlaylistSnapshotStore(appContext, sourceId).save(snapshot)
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to persist playlist snapshot: ${e.javaClass.simpleName}" }
        }
    }

    // result.text can be an 8MB (MAX_PLAYLIST_BYTES) M3U with thousands of channels - M3uParser.parse
    // and ChannelGrouper.group are CPU-bound text/collection work, not I/O, so this is its own
    // Dispatchers.Default hop rather than piggybacking on the IO dispatcher the load itself used.
    // Every caller (loadFromUrl/loadFromFile) reaches this from PlaylistController's viewModelScope,
    // i.e. Dispatchers.Main.immediate - without this hop, parsing a large playlist freezes the UI for
    // the whole duration. See docs/PERFORMANCE.md.
    private suspend fun toOutcome(
        result: PlaylistLoadResult,
        sourceFingerprint: String,
        sourceUrl: String?,
        extraEpgUrls: List<String> = emptyList(),
    ): PlaylistOutcome = when (result) {
        is PlaylistLoadResult.Success -> withContext(Dispatchers.Default) {
            val parsed = M3uParser.parse(result.text)
            PlaylistOutcome.Loaded(
                groups = ChannelGrouper.group(parsed.channels),
                skippedLineCount = parsed.skippedLineCount,
                sourceFingerprint = sourceFingerprint,
                sourceUrl = sourceUrl,
                epgUrls = parsed.epgUrls + extraEpgUrls,
            )
        }
        PlaylistLoadResult.SizeLimitExceeded -> PlaylistOutcome.SizeLimitExceeded
        is PlaylistLoadResult.HttpError -> PlaylistOutcome.HttpError(result.code)
        is PlaylistLoadResult.ReadError -> PlaylistOutcome.ReadError(result.message)
    }
}
