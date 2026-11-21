package com.uacastplayer.ui.player

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val LEVEL_SAMPLE_INTERVAL_MILLIS = 500L

/** Owned by the levels sheet: no sampling after dismissal or while the Activity is paused. */
internal suspend fun observeResumedPlayerLevels(
    lifecycle: Lifecycle,
    refresh: () -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (currentCoroutineContext().isActive) {
            refresh()
            delay(LEVEL_SAMPLE_INTERVAL_MILLIS)
        }
    }
}
