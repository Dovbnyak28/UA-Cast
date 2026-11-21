package com.uacastplayer.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Owns exactly one retry task, canceled on replacement and with its parent ViewModel scope. */
internal class PlayerRetryScheduler(
    private val scope: CoroutineScope,
    private val awaitNetwork: suspend () -> Unit,
    private val retry: () -> Unit,
) {
    private var job: Job? = null

    fun after(delayMillis: Long) = replace { delay(delayMillis) }
    fun whenNetworkAvailable() = replace(awaitNetwork)
    fun cancel() { job?.cancel(); job = null }

    private fun replace(wait: suspend () -> Unit) {
        cancel()
        job = scope.launch {
            wait()
            coroutineContext.ensureActive()
            retry()
        }
    }
}
