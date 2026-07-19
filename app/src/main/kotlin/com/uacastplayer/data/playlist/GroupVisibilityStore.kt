package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.playlist.GroupVisibilityCodec
import com.uacastplayer.playlist.GroupVisibilityEntry
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists per-playlist-source group pin/hide overrides (see [GroupVisibilityEntry]) so they
 * survive an app restart - one flat file across all sources, each entry tagged with the source it
 * belongs to. */
class GroupVisibilityStore(context: Context) {

    private val atomicFile = AtomicFile(File(context.filesDir, "group_visibility.json"))

    suspend fun load(): List<GroupVisibilityEntry> = withContext(Dispatchers.IO) {
        try {
            val bytes = atomicFile.readFully()
            GroupVisibilityCodec.decode(String(bytes, Charsets.UTF_8))
        } catch (_: FileNotFoundException) {
            emptyList()
        }
    }

    suspend fun save(entries: List<GroupVisibilityEntry>) = withContext(Dispatchers.IO) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(GroupVisibilityCodec.encode(entries).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }
}
