package com.uacastplayer.proxy

/**
 * A channel switch during a proxy-mode cast session replaces the active raw-TS remux session, but
 * the old one shouldn't be torn down the instant the new one starts (see `data/cast/ProxyServer.kt`
 * "one active remux stream per session") - the receiver may still have an in-flight request for the
 * old resource (a segment fetch already issued, a stale playlist poll) during the brief handoff
 * window, and discarding its buffered window immediately turns a channel switch into a visible
 * playback glitch on the TV even though the switch itself succeeded. The old session is instead
 * frozen and marked "draining": its upstream reader stops, while buffered segments remain servable
 * until either the new channel is confirmed loaded on the receiver (no reason to wait further) or
 * a fixed grace period elapses.
 */
object RemuxHandoffPolicy {
    const val DRAIN_TIMEOUT_MILLIS = 10_000L

    fun shouldKillDraining(confirmed: Boolean, elapsedMillis: Long): Boolean =
        confirmed || elapsedMillis >= DRAIN_TIMEOUT_MILLIS
}
