package com.uacastplayer.data.playlist

import android.content.Context
import com.uacastplayer.playlist.PlaylistSnapshot
import com.uacastplayer.playlist.PlaylistSnapshotCodec
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The single fixed-name snapshot file used before multi-playlist support (see
 * [PlaylistSourceStore]) - read-only, for one-time migration only (see
 * `PlaylistRepository.migrateLegacySnapshotIfNeeded`). Nothing writes to this file anymore; once
 * migrated it's deleted so this codepath never runs again for that install.
 */
internal object LegacyPlaylistSnapshotFile {
    private const val FILE_NAME = "playlist_snapshot.bin"

    suspend fun read(context: Context): PlaylistSnapshot? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return@withContext null
        try {
            FileInputStream(file).use { PlaylistSnapshotCodec.decode(it) }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun delete(context: Context) = withContext(Dispatchers.IO) {
        File(context.filesDir, FILE_NAME).delete()
        Unit
    }
}
