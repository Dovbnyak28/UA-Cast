package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.data.writeSafely
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PlaylistSnapshotStore"

/**
 * Persists the last successfully loaded playlist for one saved source (see
 * `com.uacastplayer.playlist.PlaylistSource`) so it survives an app restart - keyed by [sourceId]
 * (its stable fingerprint) so switching between multiple saved sources doesn't require a fresh
 * network fetch every time, only the first time a given source is loaded.
 */
class PlaylistSnapshotStore(context: Context, sourceId: String) {

    private val atomicFile = AtomicFile(File(context.filesDir, "playlist_snapshot_$sourceId.bin"))

    suspend fun save(snapshot: PlaylistSnapshot) = withContext(Dispatchers.IO) {
        atomicFile.writeSafely(TAG, "Playlist snapshot") { stream ->
            PlaylistSnapshotCodec.encode(snapshot, stream)
        }
        Unit
    }

    suspend fun load(): PlaylistSnapshot? = withContext(Dispatchers.IO) {
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
