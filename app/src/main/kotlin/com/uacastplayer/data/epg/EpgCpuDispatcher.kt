package com.uacastplayer.data.epg

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * A bounded process-lifetime lane for XMLTV parsing and index construction.
 *
 * A large guide can spend tens of seconds in non-suspending parser code. Running it on the shared
 * Default pool lets player work delay the guide indefinitely, while sharing the playlist worker
 * would make channel search wait behind that entire parse. One worker bounds EPG peak concurrency
 * and keeps both unrelated subsystems responsive.
 */
private val epgCpuDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "ua-cast-epg-cpu").apply { isDaemon = true }
}.asCoroutineDispatcher()

internal suspend fun <T> withEpgCpu(block: () -> T): T =
    withContext(epgCpuDispatcher) { block() }

/** EPG parsing can run for tens of seconds; see [withPlaylistCpuCancellable] for why a cooperative
 * probe is required even though the surrounding [withContext] itself is cancellable. */
internal suspend fun <T> withEpgCpuCancellable(block: (checkCancellation: () -> Unit) -> T): T {
    val callerContext = currentCoroutineContext()
    return withContext(epgCpuDispatcher) {
        block { callerContext.ensureActive() }
    }
}
