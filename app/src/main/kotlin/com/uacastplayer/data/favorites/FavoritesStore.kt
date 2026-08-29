package com.uacastplayer.data.favorites

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.json.JsonDecodeResult
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.data.writeSafely
import com.uacastplayer.favorites.FavoritesJsonCodec
import com.uacastplayer.log.AppLog
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "FavoritesStore"

internal interface FavoritesStorage {
    suspend fun load(): List<FavoriteChannel>
    suspend fun save(favorites: List<FavoriteChannel>)
}

internal class FavoritesStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) : FavoritesStorage {

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
    override suspend fun load(): List<FavoriteChannel> = withContext(ioDispatcher) {
        try {
            val bytes = atomicFile.readFully()
            when (val decoded = FavoritesJsonCodec.decodeResult(String(bytes, Charsets.UTF_8))) {
                is JsonDecodeResult.Success -> decoded.value
                is JsonDecodeResult.Malformed -> {
                    AppLog.w(TAG) { "Favorites data malformed, starting empty: ${decoded.failureType}" }
                    emptyList()
                }
            }
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (e: IOException) {
            AppLog.w(TAG) { "Favorites read failed, starting empty: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    override suspend fun save(favorites: List<FavoriteChannel>) = withContext(ioDispatcher) {
        atomicFile.writeSafely(TAG, "Favorites") { stream ->
            stream.write(FavoritesJsonCodec.encode(favorites).toByteArray(Charsets.UTF_8))
        }
        Unit
    }
}
