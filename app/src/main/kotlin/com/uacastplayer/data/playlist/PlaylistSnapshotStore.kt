package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.data.writeSafely
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "PlaylistSnapshotStore"

/**
 * Persists the last successfully loaded playlist for one saved source (see
 * `com.uacastplayer.playlist.PlaylistSource`) so it survives an app restart - keyed by [sourceId]
 * (its stable fingerprint) so switching between multiple saved sources doesn't require a fresh
 * network fetch every time, only the first time a given source is loaded.
 */
class PlaylistSnapshotStore(
    context: Context,
    sourceId: String,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) {

    private val atomicFile = AtomicFile(File(context.filesDir, "playlist_snapshot_$sourceId.bin"))

    suspend fun save(snapshot: PlaylistSnapshot) = withContext(ioDispatcher) {
        atomicFile.writeSafely(TAG, "Playlist snapshot") { stream ->
            PlaylistSnapshotCodec.encode(snapshot, stream)
        }
        Unit
    }

    /**
     * Removes this source's snapshot, including whatever an unfinished write left beside it.
     *
     * Here rather than in `PlaylistRepository`, which used to build the same filename a second time
     * and hand it to `File.delete()`. That missed the `.new` file `AtomicFile` writes into (see
     * [com.uacastplayer.data.cache.CachePaths.ATOMIC_WRITE_SUFFIX]), and for a *removed* source
     * that file is stranded for good: its id is never written again, so nothing will rename or
     * truncate it, and `filesDir` is not a directory Android ever reclaims. Measured at 5.6MB for a
     * 40,000-channel playlist.
     */
    suspend fun delete() = withContext(ioDispatcher) {
        atomicFile.delete()
    }

    suspend fun load(): PlaylistSnapshot? = withContext(ioDispatcher) {
        try {
            atomicFile.openRead().use { PlaylistSnapshotCodec.decode(it) }
        } catch (_: FileNotFoundException) {
            null
        } catch (e: IOException) {
            // Purely a cache - an unreadable snapshot just means re-fetching from the network,
            // which is what a null return already asks the caller to do.
            AppLog.w(TAG) { "Playlist snapshot read failed, will refetch: ${e.javaClass.simpleName}" }
            null
        }
    }
}
