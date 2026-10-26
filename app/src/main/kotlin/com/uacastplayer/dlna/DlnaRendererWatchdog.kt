package com.uacastplayer.dlna

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ends stale sessions after repeated evidence. Legacy renderers without a status action are
 * marked unverified, then checked less often for basic reachability instead of being forgotten. */
internal class DlnaRendererWatchdog(
    private val scope: CoroutineScope,
    private val read: suspend (String) -> DlnaTransportHealth,
) {
    private var job: Job? = null

    fun start(controlUrl: String, onUnverified: () -> Unit = {}, onLost: () -> Unit) {
        stop()
        job = scope.launch {
            var failures = 0
            var statusUnverified = false
            while (failures < FAILURE_LIMIT) {
                delay(if (statusUnverified) UNVERIFIED_INTERVAL_MILLIS else INTERVAL_MILLIS)
                when (read(controlUrl)) {
                    DlnaTransportHealth.ACTIVE -> failures = 0
                    DlnaTransportHealth.UNSUPPORTED -> {
                        if (!statusUnverified) {
                            statusUnverified = true
                            onUnverified()
                        }
                        // The renderer answered the SOAP request, even though it cannot report
                        // playback state. A later timeout can still prove it went away.
                        failures = 0
                    }
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
        const val UNVERIFIED_INTERVAL_MILLIS = 60_000L
        const val FAILURE_LIMIT = 3
    }
}
