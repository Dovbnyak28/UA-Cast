package com.uacastplayer.core.remote

import kotlin.math.abs

/** Swipes move D-pad focus, taps select. One bounded command per sample avoids long queues from a fast drag. */
class TouchpadNavigation(private val threshold: Float) {
    private var horizontal = 0f
    private var vertical = 0f

    init { require(threshold.isFinite() && threshold > 0f) }

    fun drag(dx: Float, dy: Float): RemoteCommand? {
        if (!dx.isFinite() || !dy.isFinite()) return null
        horizontal += dx
        vertical += dy
        val moved = maxOf(abs(horizontal), abs(vertical)) >= threshold
        val command = if (!moved) null else if (abs(horizontal) >= abs(vertical)) {
            if (horizontal > 0f) RemoteCommand.RIGHT else RemoteCommand.LEFT
        } else {
            if (vertical > 0f) RemoteCommand.DOWN else RemoteCommand.UP
        }
        if (moved) reset()
        return command
    }

    fun reset() { horizontal = 0f; vertical = 0f }
}
