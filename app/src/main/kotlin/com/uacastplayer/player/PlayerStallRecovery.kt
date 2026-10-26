package com.uacastplayer.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class PlayerStallObservation(
    val tick: StallDetectionPolicy.Tick,
    val thresholdMillis: Long,
    val mayRecover: Boolean,
)

/** Owns the sampler and delayed silent-stall action. No Media3, Android or UI state is retained.
 * The ViewModel supplies a fresh observation and executes effects on its Main-bound scope. */
internal class PlayerStallRecovery(
    private val scope: CoroutineScope,
    private val session: PlayerSessionStateMachine,
    private val observe: () -> PlayerStallObservation,
    private val onEffect: (PlayerSessionStateMachine.StallEffect) -> Unit,
    private val recover: (StallRecoveryKind) -> Unit,
) {
    private var sampler: Job? = null
    private var recovery: Job? = null
    private var generation = 0L

    fun start() {
        if (sampler?.isActive == true) return
        sampler = scope.launch {
            while (isActive) {
                delay(SAMPLE_MILLIS)
                sample()
            }
        }
    }

    fun cancel(resetBudget: Boolean = true) {
        cancelAction()
        session.cancelStallRecovery(resetBudget)
        onEffect(PlayerSessionStateMachine.StallEffect.ClearRecoveryIndicator)
    }

    fun stop() {
        sampler?.cancel()
        sampler = null
        cancel()
    }

    /** Also re-check intent after the delay: pause, remote handoff and exit invalidate recovery. */
    fun perform(attempt: Int) {
        val observation = observe()
        if (!observation.mayRecover || !observation.tick.playWhenReady) {
            cancel()
        } else {
            recover(StallRetryPolicy.recoveryKindFor(attempt))
        }
    }

    private fun sample() {
        val observation = observe()
        if (!observation.mayRecover || !observation.tick.playWhenReady) return
        val effect = session.onStallTick(observation.tick, observation.thresholdMillis)
        when (effect) {
            PlayerSessionStateMachine.StallEffect.None -> Unit
            PlayerSessionStateMachine.StallEffect.ClearRecoveryIndicator,
            PlayerSessionStateMachine.StallEffect.GiveUp,
            -> cancelAction()
            is PlayerSessionStateMachine.StallEffect.ScheduleRecovery -> {
                cancelAction()
                val expectedGeneration = generation
                recovery = scope.launch {
                    delay(effect.delayMillis)
                    if (generation == expectedGeneration) perform(effect.attempt)
                }
            }
        }
        onEffect(effect)
    }

    private fun cancelAction() {
        generation++
        recovery?.cancel()
        recovery = null
    }

    private companion object {
        const val SAMPLE_MILLIS = 2_000L
    }
}
