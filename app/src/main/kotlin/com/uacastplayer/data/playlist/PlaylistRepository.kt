package com.uacastplayer.data.playlist

import android.content.Context
import android.net.Uri
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.data.prefs.currentAppLanguage
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
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher

sealed interface PlaylistOutcome {
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
    ) : PlaylistOutcome
    data object SizeLimitExceeded : PlaylistOutcome
    data class HttpError(val code: Int) : PlaylistOutcome
    data class ReadError(val message: String?) : PlaylistOutcome
}

private const val TAG = "PlaylistRepository"
private const val TRACE_ID_LENGTH = 8

class PlaylistRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) {

    private val appContext = context.applicationContext
    private val httpClient = AppHttp.client(connectTimeoutSeconds = 15, readTimeoutSeconds = 30)
    private val urlLoader = PlaylistUrlLoader(httpClient, ioDispatcher)
    private val fileLoader = PlaylistFileLoader(appContext, ioDispatcher)
    private val sourceStore = PlaylistSourceStore(appContext, ioDispatcher)
    private val snapshotMutations = PlaylistSnapshotMutationCoordinator()

    /**
     * The alphabet the group list is ordered by - the language the user chose in this app, not the
     * device's. Read per call rather than cached: changing the language rebuilds the playlist, and
     * a value captured at construction would keep the old alphabet until the process restarted.
     */
    private fun groupingLocale(): Locale = appContext.currentAppLanguage().toLocale()

    /** [extraEpgUrls] lets an Xtream-built URL (see XtreamUrlBuilder) contribute its synthesized
     * xmltv.php address even when the M3U's own #EXTM3U header doesn't advertise one. */
    suspend fun loadFromUrl(url: String, extraEpgUrls: List<String> = emptyList()): PlaylistOutcome {
        val sourceId = Fingerprint.of(url)
        // Captured before network/parse work. If this source is removed while either is running,
        // its eventual non-cancellable AtomicFile stage is rejected as stale.
        val snapshotLease = snapshotMutations.captureWrite(sourceId)
        val traceId = sourceId.take(TRACE_ID_LENGTH)
        return try {
            AppLog.d(TAG) { "Playlist $traceId download started" }
            val loaded = urlLoader.load(url)
            AppLog.d(TAG) { "Playlist $traceId download finished (${loaded.javaClass.simpleName})" }
            val outcome = toOutcome(loaded, sourceId, sourceUrl = url, extraEpgUrls = extraEpgUrls)
            AppLog.d(TAG) { "Playlist $traceId parse finished (${outcome.javaClass.simpleName})" }
            persistIfLoaded(snapshotLease, outcome)
            AppLog.d(TAG) { "Playlist $traceId snapshot stage finished" }
            outcome
        } catch (cancelled: CancellationException) {
            AppLog.d(TAG) { "Playlist $traceId load cancelled" }
            throw cancelled
        }
    }

    /** The file name behind a picked document, for naming the source - see
     * [PlaylistFileLoader.documentName]. */
    fun documentName(uri: Uri): String? = fileLoader.documentName(uri)

    suspend fun loadFromFile(uri: Uri): PlaylistOutcome {
        // Asked for on every load, not only the first: taking a grant already held is a no-op, and
        // this is the one place every path to a file playlist goes through - the initial pick, a
        // source switch, and a reload from the saved list. See PlaylistFileLoader.rememberAccess.
        fileLoader.rememberAccess(uri)
        val sourceId = Fingerprint.of(uri.toString())
        val snapshotLease = snapshotMutations.captureWrite(sourceId)
        val outcome = toOutcome(fileLoader.load(uri), sourceId, sourceUrl = null)
        persistIfLoaded(snapshotLease, outcome)
        return outcome
    }

    /** Cached channels for one saved source (see [PlaylistSource]) - keyed by [sourceId] so
     * switching between multiple saved sources can show the last-known channels immediately
     * instead of always re-fetching over the network. Grouping thousands of channels is CPU work,
     * not disk I/O, so it gets its own playlist CPU hop rather than riding along on the dispatcher
     * used for the snapshot file read. */
    suspend fun restoreSnapshot(sourceId: String): PlaylistOutcome? {
        val snapshot = PlaylistSnapshotStore(appContext, sourceId, ioDispatcher).load() ?: return null
        return withPlaylistCpuCancellable { checkCancellation ->
            PlaylistOutcome.Loaded(
                groups = ChannelGrouper.group(snapshot.channels, groupingLocale(), checkCancellation),
                skippedLineCount = snapshot.skippedLineCount,
                sourceFingerprint = snapshot.sourceFingerprint,
                sourceUrl = snapshot.sourceUrl,
            )
        }
    }

    suspend fun loadSources(): List<PlaylistSource> = sourceStore.load()

    suspend fun saveSources(sources: List<PlaylistSource>) = sourceStore.save(sources)

    /** Delegated rather than reaching for the file directly - this used to spell the snapshot's
     * name out a second time and delete only that one name, which left `AtomicFile`'s in-progress
     * file behind. See [PlaylistSnapshotStore.delete]. */
    suspend fun deleteSnapshot(sourceId: String) {
        snapshotMutations.invalidateAndDelete(sourceId) {
            PlaylistSnapshotStore(appContext, sourceId, ioDispatcher).delete()
        }
    }

    /**
     * One-time upgrade path: before multi-playlist support there was exactly one snapshot file
     * (see [LegacyPlaylistSnapshotFile]) and no source list at all. Called only when [loadSources]
     * comes back empty - adopts that single snapshot as the first saved source and copies its bytes
     * to the new per-source file. Returns null when there was nothing to migrate (fresh install).
     *
     * **It does not delete the old file**, and that is the whole point of splitting this in two.
     * The migration is not finished when the bytes are copied; it is finished when the source list
     * naming them is on disk, and that write belongs to the caller. Deleting here meant a process
     * death in between - a low-memory kill during the first launch after an update, which is
     * exactly when the app is doing the most work - left the sources list empty and the legacy file
     * gone, so the next launch found nothing to migrate and the playlist was lost while its bytes
     * sat in an orphaned file nothing referenced. The caller calls [discardLegacySnapshot] once the
     * commit it is part of has actually landed.
     */
    suspend fun migrateLegacySnapshotIfNeeded(): PlaylistSource? {
        val legacy = LegacyPlaylistSnapshotFile.read(appContext, ioDispatcher) ?: return null
        val id = legacy.sourceFingerprint.ifBlank { Fingerprint.of(legacy.sourceUrl.orEmpty()) }
        val type = if (legacy.sourceUrl != null) PlaylistSourceType.URL else PlaylistSourceType.FILE
        PlaylistSnapshotStore(appContext, id, ioDispatcher).save(legacy)
        return PlaylistSource(
            id = id,
            type = type,
            location = legacy.sourceUrl.orEmpty(),
            displayName = null,
            addedAtEpochMillis = legacy.savedAtEpochMillis,
        )
    }

    /**
     * Retires the pre-multi-playlist snapshot file, after the source list that replaces it has been
     * written. Safe to call when there is nothing there - a re-run of a migration that already
     * completed simply finds no file.
     */
    suspend fun discardLegacySnapshot() {
        LegacyPlaylistSnapshotFile.delete(appContext, ioDispatcher)
    }

    // Best-effort cache write: the playlist this session already loaded is usable either way, so
    // any persist failure (disk full, I/O error) should just skip the cache, not fail the load.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun persistIfLoaded(
        lease: PlaylistSnapshotMutationCoordinator.WriteLease,
        outcome: PlaylistOutcome,
    ) {
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
            val persisted = snapshotMutations.runWriteIfCurrent(lease) {
                PlaylistSnapshotStore(appContext, lease.sourceId, ioDispatcher).save(snapshot)
            }
            if (!persisted) AppLog.d(TAG) { "Skipped stale snapshot write for removed playlist" }
        } catch (e: CancellationException) {
            // The save suspends, so a scope ending here would otherwise be swallowed as a failed
            // cache write and the caller would carry on inside a cancelled scope.
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG) { "Failed to persist playlist snapshot: ${e.javaClass.simpleName}" }
        }
    }

    // result.text can be an 8MB (MAX_PLAYLIST_BYTES) M3U with thousands of channels - M3uParser.parse
    // and ChannelGrouper.group are CPU-bound text/collection work, not I/O, so this is its own
    // dedicated CPU hop rather than piggybacking on the IO dispatcher the load itself used. It is
    // deliberately not Dispatchers.Default: Media3 can saturate that shared pool during recovery,
    // and a downloaded playlist must not remain permanently queued behind player work.
    // Every caller (loadFromUrl/loadFromFile) reaches this from PlaylistController's viewModelScope,
    // i.e. Dispatchers.Main.immediate - without this hop, parsing a large playlist freezes the UI for
    // the whole duration. See docs/PERFORMANCE.md.
    private suspend fun toOutcome(
        result: PlaylistLoadResult,
        sourceFingerprint: String,
        sourceUrl: String?,
        extraEpgUrls: List<String> = emptyList(),
    ): PlaylistOutcome = when (result) {
        is PlaylistLoadResult.Success -> {
            val traceId = sourceFingerprint.take(TRACE_ID_LENGTH)
            AppLog.d(TAG) { "Playlist $traceId parse worker queued" }
            withPlaylistCpuCancellable { checkCancellation ->
                AppLog.d(TAG) { "Playlist $traceId parse worker started" }
                val parsed = M3uParser.parse(result.text, checkCancellation)
                AppLog.d(TAG) { "Playlist $traceId M3U parsed" }
                val groups = ChannelGrouper.group(parsed.channels, groupingLocale(), checkCancellation)
                AppLog.d(TAG) { "Playlist $traceId channels grouped" }
                PlaylistOutcome.Loaded(
                    groups = groups,
                    skippedLineCount = parsed.skippedLineCount,
                    sourceFingerprint = sourceFingerprint,
                    sourceUrl = sourceUrl,
                    epgUrls = parsed.epgUrls + extraEpgUrls,
                )
            }
        }
        PlaylistLoadResult.SizeLimitExceeded -> PlaylistOutcome.SizeLimitExceeded
        is PlaylistLoadResult.HttpError -> PlaylistOutcome.HttpError(result.code)
        is PlaylistLoadResult.ReadError -> PlaylistOutcome.ReadError(result.message)
    }
}
