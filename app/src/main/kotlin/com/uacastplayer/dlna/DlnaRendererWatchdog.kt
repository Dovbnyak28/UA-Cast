package com.uacastplayer.dlna

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ends stale sessions after repeated evidence; unsupported legacy renderers are left alone. */
internal class DlnaRendererWatchdog(
    private val scope: CoroutineScope,
    private val read: suspend (String) -> DlnaTransportHealth,
) {
    private var job: Job? = null

    fun start(controlUrl: String, onLost: () -> Unit) {
        stop()
        job = scope.launch {
            var failures = 0
            while (failures < FAILURE_LIMIT) {
                delay(INTERVAL_MILLIS)
                when (read(controlUrl)) {
                    DlnaTransportHealth.ACTIVE -> failures = 0
                    DlnaTransportHealth.UNSUPPORTED -> return@launch
                    DlnaTransportHealth.INACTIVE, DlnaTransportHealth.UNREACHABLE -> failures++
                }
            }
            onLost()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val INTERVAL_MILLIS = 20_000L
        const val FAILURE_LIMIT = 3
    }
}
