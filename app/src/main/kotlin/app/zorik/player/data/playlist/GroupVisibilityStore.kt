package app.zorik.player.data.playlist

import android.content.Context
import androidx.core.util.AtomicFile
import app.zorik.player.core.concurrent.AppDispatchers
import app.zorik.player.core.json.JsonDecodeResult
import app.zorik.player.data.writeSafely
import app.zorik.player.log.AppLog
import app.zorik.player.playlist.GroupVisibilityCodec
import app.zorik.player.playlist.GroupVisibilityEntry
import app.zorik.player.playlist.GroupVisibilityStorage
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "GroupVisibilityStore"

/** Persists per-playlist-source group pin/hide overrides (see [GroupVisibilityEntry]) so they
 * survive an app restart - one flat file across all sources, each entry tagged with the source it
 * belongs to. */
class GroupVisibilityStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) : GroupVisibilityStorage {

    private val atomicFile = AtomicFile(File(context.filesDir, "group_visibility.json"))

    override suspend fun load(): List<GroupVisibilityEntry> = withContext(ioDispatcher) {
        try {
            val bytes = atomicFile.readFully()
            when (val decoded = GroupVisibilityCodec.decodeResult(String(bytes, Charsets.UTF_8))) {
                is JsonDecodeResult.Success -> decoded.value
                is JsonDecodeResult.Malformed -> {
                    AppLog.w(TAG) { "Group visibility data malformed, starting empty: ${decoded.failureType}" }
                    emptyList()
                }
            }
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (e: IOException) {
            // See ParentalControlStore.load - same reasoning, same crash-on-start if it escapes.
            AppLog.w(TAG) { "Group visibility read failed, starting empty: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    override suspend fun save(entries: List<GroupVisibilityEntry>) = withContext(ioDispatcher) {
        val saved = atomicFile.writeSafely(TAG, "Group visibility") { stream ->
            stream.write(GroupVisibilityCodec.encode(entries).toByteArray(Charsets.UTF_8))
        }
        // Do not let a failed AtomicFile commit look like a durable settings change to the
        // coalescing writer. The in-memory UI remains usable, while the failure is logged.
        if (!saved) throw IOException("Group visibility persistence failed")
        Unit
    }
}
