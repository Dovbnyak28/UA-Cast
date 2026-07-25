package com.uacastplayer.player

/**
 * The pure, Android-free decision behind [PlayerViewModel]'s process-wide single-instance guard.
 *
 * The app must never have two [PlayerViewModel]s - and therefore two ExoPlayers - alive at once:
 * that is precisely the leak that exhausted the heap and crashed the app with OutOfMemoryError. The
 * live-instance count itself is an AtomicInteger owned by [PlayerViewModel] (which has Android
 * dependencies and can't be unit-tested); this object holds only the trivially testable
 * interpretation of that count so the "what counts as a leak" rule is covered by a plain unit test.
 */
object PlayerInstanceGuard {
    /** True when [liveCount] indicates a leak: more than one instance is alive at the same time. */
    fun isLeak(liveCount: Int): Boolean = liveCount > 1
}
