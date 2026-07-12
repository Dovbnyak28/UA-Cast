package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists the last successfully loaded playlist so it survives an app restart. */
class PlaylistSnapshotStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "playlist_snapshot.bin"))

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
