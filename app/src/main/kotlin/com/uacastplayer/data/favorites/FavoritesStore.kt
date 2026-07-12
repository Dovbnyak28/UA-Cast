package com.uacastplayer.data.favorites

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoritesJsonCodec
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoritesStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "favorites.json"))

    suspend fun load(): List<FavoriteChannel> = withContext(Dispatchers.IO) {
        try {
            val bytes = atomicFile.readFully()
            FavoritesJsonCodec.decode(String(bytes, Charsets.UTF_8))
        } catch (_: FileNotFoundException) {
            emptyList()
        }
    }

    suspend fun save(favorites: List<FavoriteChannel>) = withContext(Dispatchers.IO) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(FavoritesJsonCodec.encode(favorites).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }
}
