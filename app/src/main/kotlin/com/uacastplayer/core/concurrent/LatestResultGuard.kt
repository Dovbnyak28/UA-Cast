package com.uacastplayer.core.concurrent

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic identity for asynchronous work where only the newest result may be applied.
 *
 * [next] starts a replacement operation; [invalidate] makes an operation stale without starting
 * another one (for example when its owning session ends). The atomic counter keeps callbacks safe
 * even when a third-party API delivers them off the owner's coroutine dispatcher.
 */
internal class LatestResultGuard {
    private val value = AtomicLong(0)

    val current: Long
        get() = value.get()

    fun next(): Long = value.incrementAndGet()

    fun invalidate() {
        value.incrementAndGet()
    }

    fun isCurrent(generation: Long): Boolean = generation == value.get()
}
