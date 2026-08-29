package com.uacastplayer.data.parentalcontrol

import android.content.Context
import androidx.core.util.AtomicFile
import com.uacastplayer.core.concurrent.AppDispatchers
import com.uacastplayer.core.json.JsonDecodeResult
import com.uacastplayer.data.writeSafely
import com.uacastplayer.log.AppLog
import com.uacastplayer.parentalcontrol.LockedChannelsCodec
import com.uacastplayer.parentalcontrol.LockedChannelsStorage
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "ParentalControlStore"

/** Persists the set of parental-control-locked channel keys (see
 * [com.uacastplayer.favorites.FavoriteKey]) so they survive an app restart - deliberately not
 * scoped per playlist source, since [com.uacastplayer.favorites.FavoriteKey] is itself already
 * stable across sources (same reasoning [com.uacastplayer.data.favorites.FavoritesStore] uses). */
class ParentalControlStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.io,
) : LockedChannelsStorage {

    private val atomicFile = AtomicFile(File(context.filesDir, "parental_control_locked_channels.json"))

    override suspend fun load(): Set<String> = withContext(ioDispatcher) {
        try {
            val bytes = atomicFile.readFully()
            when (val decoded = LockedChannelsCodec.decodeResult(String(bytes, Charsets.UTF_8))) {
                is JsonDecodeResult.Success -> decoded.value
                is JsonDecodeResult.Malformed -> {
                    AppLog.w(TAG) { "Locked channels data malformed, starting empty: ${decoded.failureType}" }
                    emptySet()
                }
            }
        } catch (_: FileNotFoundException) {
            emptySet()
        } catch (e: IOException) {
            // AtomicFile.readFully already falls back to its backup copy, so reaching here means
            // neither is readable - there is nothing left to recover, and letting this escape into
            // the controller's init launch crashed the app on every start.
            AppLog.w(TAG) { "Locked channels read failed, starting empty: ${e.javaClass.simpleName}" }
            emptySet()
        }
    }

    /** A lost write here fails safe in the direction that matters: a lock that did not persist is
     * re-applied by the user, while an unlock that did not persist leaves the channel locked. */
    override suspend fun save(keys: Set<String>) = withContext(ioDispatcher) {
        atomicFile.writeSafely(TAG, "Locked channels") { stream ->
            stream.write(LockedChannelsCodec.encode(keys).toByteArray(Charsets.UTF_8))
        }
        Unit
    }
}
