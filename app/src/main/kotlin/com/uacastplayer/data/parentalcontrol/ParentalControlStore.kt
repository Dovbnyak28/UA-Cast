package com.uacastplayer.data.parentalcontrol

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.parentalcontrol.LockedChannelsCodec
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists the set of parental-control-locked channel keys (see
 * [com.uacastplayer.favorites.FavoriteKey]) so they survive an app restart - deliberately not
 * scoped per playlist source, since [com.uacastplayer.favorites.FavoriteKey] is itself already
 * stable across sources (same reasoning [com.uacastplayer.data.favorites.FavoritesStore] uses). */
class ParentalControlStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "parental_control_locked_channels.json"))

    suspend fun load(): Set<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = atomicFile.readFully()
            LockedChannelsCodec.decode(String(bytes, Charsets.UTF_8))
        } catch (_: FileNotFoundException) {
            emptySet()
        }
    }

    // AtomicFile requires failWrite() on *any* failure mid-write to release the temp file, not just
    // specific ones - the broad catch exists to make that cleanup unconditional before rethrowing.
    @Suppress("TooGenericExceptionCaught")
    suspend fun save(keys: Set<String>) = withContext(Dispatchers.IO) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(LockedChannelsCodec.encode(keys).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }
}
