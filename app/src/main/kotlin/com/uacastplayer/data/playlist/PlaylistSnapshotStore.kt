package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persists the last successfully loaded playlist for one saved source (see
 * `com.uacastplayer.playlist.PlaylistSource`) so it survives an app restart - keyed by [sourceId]
 * (its stable fingerprint) so switching between multiple saved sources doesn't require a fresh
 * network fetch every time, only the first time a given source is loaded.
 */
class PlaylistSnapshotStore(context: Context, sourceId: String) {

    private val atomicFile = AtomicFile(File(context.filesDir, "playlist_snapshot_$sourceId.bin"))

    // AtomicFile requires failWrite() on *any* failure mid-write to release the temp file, not just
    // specific ones - the broad catch exists to make that cleanup unconditional before rethrowing.
    @Suppress("TooGenericExceptionCaught")
    suspend fun save(snapshot: PlaylistSnapshot) = withContext(Dispatchers.IO) {
        val stream = atomicFile.startWrite()
        try {
            PlaylistSnapshotCodec.encode(snapshot, stream)
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    suspend fun load(): PlaylistSnapshot? = withContext(Dispatchers.IO) {
        try {
            atomicFile.openRead().use { PlaylistSnapshotCodec.decode(it) }
        } catch (_: FileNotFoundException) {
            null
        }
    }
}
