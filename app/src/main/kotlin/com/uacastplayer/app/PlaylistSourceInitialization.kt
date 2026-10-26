package com.uacastplayer.app

import com.uacastplayer.data.playlist.PlaylistOutcome
import com.uacastplayer.data.playlist.PlaylistRepository
import com.uacastplayer.data.prefs.AppPreferences
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.PlaylistChannelLimitExceededException
import com.uacastplayer.playlist.PlaylistSource
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * The saved source list is not a channel request: cancelling a superseded channel load must not
 * cancel its initial read. Every add/import waits for this owner-scoped, shared barrier before
 * checking capacity or replacing the list. Cancelling the ViewModel still cancels the read.
 */
class PlaylistSourceInitialization(
    private val preferences: AppPreferences,
    private val repository: PlaylistRepository,
    private val scope: CoroutineScope,
    private val onLoaded: (List<PlaylistSource>) -> Unit,
) {
    private val lock = Any()
    private var loading: Deferred<PlaylistOutcome?>? = null

    private fun createLoad(): Deferred<PlaylistOutcome?> = scope.async(start = CoroutineStart.LAZY) {
        var failure: PlaylistOutcome? = null
        val sources = try {
            readAndMigrate()
        } catch (_: PlaylistChannelLimitExceededException) {
            failure = PlaylistOutcome.ChannelLimitExceeded
            emptyList()
        } catch (error: IOException) {
            AppLog.w("PlaylistSourceInitialization") { "Source initialization failed: ${error.javaClass.simpleName}" }
            failure = PlaylistOutcome.StorageError
            emptyList()
        }
        onLoaded(sources)
        failure
    }

    /** A failed migration may be retried after storage is repaired without restarting the app. */
    suspend fun awaitLoaded(): PlaylistOutcome? {
        val read = synchronized(lock) {
            loading ?: createLoad().also { loading = it }
        }
        val failure = read.await()
        if (failure != null) synchronized(lock) {
            if (loading === read) loading = null
        }
        return failure
    }

    private suspend fun readAndMigrate(): List<PlaylistSource> {
        val sources = repository.loadSources()
        val migrated = if (sources.isEmpty()) repository.migrateLegacySnapshotIfNeeded() else null
        return migrated?.let { source ->
            val migratedSources = listOf(source.copy(displayName = preferences.playlistDisplayName))
            // Keep the only legacy copy until the source list naming its new snapshot is durable.
            if (repository.saveSources(migratedSources)) repository.discardLegacySnapshot()
            migratedSources
        } ?: sources
    }
}
