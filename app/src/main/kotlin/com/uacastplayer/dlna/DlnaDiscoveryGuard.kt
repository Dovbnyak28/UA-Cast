package com.uacastplayer.dlna

import com.uacastplayer.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// Same tag the repository logs under: this used to live there.
private const val TAG = "DlnaSessionRepository"

/** A last-resort net under [SsdpDiscovery]'s own per-stage catches - discovery failing must
 * leave the sheet with an empty list, never propagate. Cancellation is NOT a failure and is
 * rethrown (the sheet cancels this the moment it's dismissed), which is exactly what a
 * Kotlin's `runCatching` here would have swallowed once [SsdpDiscovery.discover] became suspending. */
@Suppress("TooGenericExceptionCaught")
internal suspend fun SsdpDiscovery.discoverOrEmpty(ioDispatcher: CoroutineDispatcher): List<DlnaDevice> =
    withContext(ioDispatcher) {
        try {
            discover()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG) { "Discovery failed: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }
