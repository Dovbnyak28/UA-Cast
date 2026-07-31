package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceCodec
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists the list of saved playlist sources (see [PlaylistSource]) so they survive an app restart. */
class PlaylistSourceStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "playlist_sources.bin"))

    // AtomicFile requires failWrite() on *any* failure mid-write to release the temp file, not just
    // specific ones - the broad catch exists to make that cleanup unconditional before rethrowing.
    @Suppress("TooGenericExceptionCaught")
    suspend fun save(sources: List<PlaylistSource>) = withContext(Dispatchers.IO) {
        val stream = atomicFile.startWrite()
        try {
            PlaylistSourceCodec.encode(sources, stream)
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    suspend fun load(): List<PlaylistSource> = withContext(Dispatchers.IO) {
        try {
            atomicFile.openRead().use { PlaylistSourceCodec.decode(it) }
        } catch (_: FileNotFoundException) {
            emptyList()
        }
    }
}
