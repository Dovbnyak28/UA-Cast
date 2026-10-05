package com.uacastplayer.cast

/** Prevents a delayed lifecycle callback from an old SDK session tearing down a newer session. */
internal object CastSessionIdentityGuard {
    fun isCurrent(eventSession: Any, currentSession: Any?): Boolean =
        currentSession != null && eventSession === currentSession
}
