package com.uacastplayer.proxy

/**
 * A channel switch during a proxy-mode cast session replaces the active raw-TS remux session, but
 * the old one shouldn't be torn down the instant the new one starts (see `data/cast/ProxyServer.kt`
 * "one active remux stream per session") - the receiver may still have an in-flight request for the
 * old resource (a segment fetch already issued, a stale playlist poll) during the brief handoff
 * window, and killing it immediately turns a channel switch into a visible playback glitch on the
 * TV even though the switch itself succeeded. The old session is instead marked "draining" and kept
 * servable until either the new channel is confirmed actually loaded on the receiver (no reason to
 * wait any further) or a fixed grace period elapses (the receiver never confirmed - a slow or
 * failed load shouldn't hold the old session's thread and upstream connection open forever).
 */
object RemuxHandoffPolicy {
    const val DRAIN_TIMEOUT_MILLIS = 10_000L

    fun shouldKillDraining(confirmed: Boolean, elapsedMillis: Long): Boolean =
        confirmed || elapsedMillis >= DRAIN_TIMEOUT_MILLIS
}
