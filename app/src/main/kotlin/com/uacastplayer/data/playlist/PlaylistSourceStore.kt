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

    /**
     * Writes the saved sources - unless the file already there was written by a newer build.
     *
     * This is the one file in the app whose loss is permanent. A snapshot or a cache that cannot be
     * read is re-fetched; the source list *is* the user's playlists, and there is nowhere else to
     * get it from. An older build reads a newer format as an empty list (see [PlaylistSourceCodec]),
     * so without this guard the first playlist added after a rollback would overwrite every entry
     * of the format it could not read, and re-upgrading afterwards would find them gone rather than
     * waiting.
     *
     * Refusing the write is the recoverable failure: the user's new playlist is not saved this
     * session, and everything they already had survives the trip back up.
     */
    suspend fun save(sources: List<PlaylistSource>) = withContext(Dispatchers.IO) {
        if (writtenByANewerBuild()) {
            AppLog.w(TAG) { "Playlist sources on disk are from a newer format - not overwriting them" }
            return@withContext
        }
        atomicFile.writeSafely(TAG, "Playlist sources") { stream ->
            PlaylistSourceCodec.encode(sources, stream)
        }
        Unit
    }

    private fun writtenByANewerBuild(): Boolean = try {
        atomicFile.openRead().use { PlaylistSourceCodec.isFromANewerFormat(it) }
    } catch (_: FileNotFoundException) {
        false
    } catch (_: IOException) {
        // Unreadable is not the same as newer, and a corrupt file must stay overwritable or the
        // app could never save a playlist again.
        false
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
