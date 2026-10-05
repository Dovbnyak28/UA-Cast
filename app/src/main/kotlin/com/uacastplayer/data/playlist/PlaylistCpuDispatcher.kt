package com.uacastplayer.data.playlist

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Keeps playlist parsing and indexing independent from Media3 and other users of
 * [kotlinx.coroutines.Dispatchers.Default].
 *
 * A player recovery storm can occupy every worker in the shared CPU pool. Playlist loads then
 * finish their network request but never begin parsing, leaving the UI in its loading state. A
 * single process-lifetime worker is enough because [com.uacastplayer.app.PlaylistController]
 * permits only one active load, and bounding this work also avoids multiplying the peak memory of
 * large playlists.
 *
 * The thread is a daemon so JVM tests can terminate without an explicit application lifecycle
 * callback. Android tears it down with the app process.
 */
private val playlistCpuDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "ua-cast-playlist-cpu").apply { isDaemon = true }
}.asCoroutineDispatcher()

internal suspend fun <T> withPlaylistCpu(block: () -> T): T =
    withContext(playlistCpuDispatcher) { block() }

/**
 * CPU work is not interrupted automatically when the calling coroutine is cancelled. Supplies a
 * cheap probe that a long parser can invoke periodically so a superseded load releases this single
 * lane promptly instead of making the replacement wait behind work whose result nobody wants.
 */
internal suspend fun <T> withPlaylistCpuCancellable(block: (checkCancellation: () -> Unit) -> T): T {
    val callerContext = currentCoroutineContext()
    return withContext(playlistCpuDispatcher) {
        block { callerContext.ensureActive() }
    }
}
