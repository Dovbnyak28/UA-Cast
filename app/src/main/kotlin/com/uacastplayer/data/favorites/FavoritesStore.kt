package com.uacastplayer.data.favorites

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.data.writeSafely
import com.uacastplayer.favorites.FavoritesJsonCodec
import com.uacastplayer.log.AppLog
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FavoritesStore"

class FavoritesStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "favorites.json"))

    /**
     * [IOException], not just [FileNotFoundException]: the narrower catch handled "no favorites
     * saved yet" and let every other read failure escape into
     * [com.uacastplayer.data.favorites.FavoritesRepository]'s init `launch`, where nothing catches
     * it - so an unreadable favorites file crashed the app on startup, every startup.
     *
     * [AtomicFile.readFully] already falls back to its backup copy, so reaching this catch means
     * neither copy could be read; there is nothing left to preserve and an empty list is the only
     * answer available. Logged so the difference between "none saved" and "could not read" is
     * visible in a diagnostics report.
     */
    suspend fun load(): List<FavoriteChannel> = withContext(Dispatchers.IO) {
        try {
            val bytes = atomicFile.readFully()
            FavoritesJsonCodec.decode(String(bytes, Charsets.UTF_8))
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (e: IOException) {
            AppLog.w(TAG) { "Favorites read failed, starting empty: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    suspend fun save(favorites: List<FavoriteChannel>) = withContext(Dispatchers.IO) {
        atomicFile.writeSafely(TAG, "Favorites") { stream ->
            stream.write(FavoritesJsonCodec.encode(favorites).toByteArray(Charsets.UTF_8))
        }
        Unit
    }
}
