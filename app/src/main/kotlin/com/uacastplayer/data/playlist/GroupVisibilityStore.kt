package com.uacastplayer.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.data.writeSafely
import com.uacastplayer.log.AppLog
import com.uacastplayer.playlist.GroupVisibilityCodec
import com.uacastplayer.playlist.GroupVisibilityEntry
import com.uacastplayer.playlist.GroupVisibilityStorage
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "GroupVisibilityStore"

/** Persists per-playlist-source group pin/hide overrides (see [GroupVisibilityEntry]) so they
 * survive an app restart - one flat file across all sources, each entry tagged with the source it
 * belongs to. */
class GroupVisibilityStore(context: Context) : GroupVisibilityStorage {

    private val atomicFile = AtomicFile(File(context.filesDir, "group_visibility.json"))

    override suspend fun load(): List<GroupVisibilityEntry> = withContext(Dispatchers.IO) {
        try {
            val bytes = atomicFile.readFully()
            GroupVisibilityCodec.decode(String(bytes, Charsets.UTF_8))
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (e: IOException) {
            // See ParentalControlStore.load - same reasoning, same crash-on-start if it escapes.
            AppLog.w(TAG) { "Group visibility read failed, starting empty: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    override suspend fun save(entries: List<GroupVisibilityEntry>) = withContext(Dispatchers.IO) {
        atomicFile.writeSafely(TAG, "Group visibility") { stream ->
            stream.write(GroupVisibilityCodec.encode(entries).toByteArray(Charsets.UTF_8))
        }
        Unit
    }
}
