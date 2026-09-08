package com.uacastplayer.player

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owned by the playback ViewModel, not by whichever full/mini screen currently renders it. */
class PlayerSleepTimer(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val onExpire: () -> Unit,
) {
    private var timerJob: Job? = null
    private val remaining = MutableStateFlow<Long?>(null)
    val remainingMillis = remaining.asStateFlow()

    fun start(duration: Duration) {
        cancel()
        val end = SleepTimerCalculator.endTimeMillis(nowMillis(), duration)
        remaining.value = duration.inWholeMilliseconds.coerceAtLeast(0)
        timerJob = scope.launch {
            while (!SleepTimerCalculator.hasExpired(nowMillis(), end)) {
                remaining.value = SleepTimerCalculator.remainingMillis(nowMillis(), end)
                delay(TICK_MILLIS)
            }
            remaining.value = null
            onExpire()
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        remaining.value = null
    }
}

private const val TICK_MILLIS = 1_000L
private const val NANOS_PER_MILLI = 1_000_000L
