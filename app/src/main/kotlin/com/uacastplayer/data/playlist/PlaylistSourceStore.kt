package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.data.writeSafely
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceCodec
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PlaylistSourceStore"

/** Persists the list of saved playlist sources (see [PlaylistSource]) so they survive an app restart. */
class PlaylistSourceStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "playlist_sources.bin"))

    suspend fun save(sources: List<PlaylistSource>) = withContext(Dispatchers.IO) {
        atomicFile.writeSafely(TAG, "Playlist sources") { stream ->
            PlaylistSourceCodec.encode(sources, stream)
        }
        Unit
    }

    suspend fun load(): List<PlaylistSource> = withContext(Dispatchers.IO) {
        try {
            atomicFile.openRead().use { PlaylistSourceCodec.decode(it) }
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (e: IOException) {
            // See ParentalControlStore.load - an unreadable file must degrade to "no sources
            // saved", not escape into PlaylistController's loadInitialSource launch.
            AppLog.w(TAG) { "Playlist sources read failed, starting empty: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }
}
