package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
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

    /**
     * Through [AtomicFile] because that is what wrote this file: until multi-playlist support it
     * was `AtomicFile(File(filesDir, "playlist_snapshot.bin"))`, so an install killed mid-write by
     * the old build left a `playlist_snapshot.bin.new` beside it. Deleting the base name alone
     * stranded that one on the first launch after the upgrade, at which point nothing in the app
     * refers to either name again.
     */
    suspend fun delete(context: Context) = withContext(Dispatchers.IO) {
        AtomicFile(File(context.filesDir, FILE_NAME)).delete()
    }
}
