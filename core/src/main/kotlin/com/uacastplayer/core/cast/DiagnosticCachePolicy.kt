package com.uacastplayer.core.cast

/** A single [CastCompatibilityPolicy] verdict cached for a stream URL, plus the [TsSourceKind] it
 * was probed as and when it was cached - see [DiagnosticCachePolicy]. */
data class CachedDiagnostic(
    val verdict: CastCompatibilityVerdict,
    val sourceKind: TsSourceKind,
    val cachedAtMillis: Long,
)

/**
 * Governs `data/cast/DiagnosticResultCache.kt` (an LRU cache of [MAX_ENTRIES] entries keyed by
 * stream URL) - see `cast/CastSessionRepository`'s diagnostic warm-up and watchdog-race wiring.
 * Probing a stream is a real HTTP fetch, so a channel that's cast (or warmed up while just
 * browsing) more than once shouldn't pay for it every time - but an [CastCompatibilityVerdict.Unknown]
 * verdict (PAT/PMT not found in the probe window) might just have caught the origin at a bad moment
 * (mid-ad-break, a transient encoder hiccup), so it's only trusted for a short [UNKNOWN_TTL_MILLIS]
 * rather than the rest of the process's lifetime like every other verdict.
 */
object DiagnosticCachePolicy {
    const val MAX_ENTRIES = 32
    const val DEBOUNCE_MILLIS = 1_500L
    const val UNKNOWN_TTL_MILLIS = 10 * 60_000L

    fun isValid(entry: CachedDiagnostic, nowMillis: Long): Boolean =
        entry.verdict != CastCompatibilityVerdict.Unknown || nowMillis - entry.cachedAtMillis < UNKNOWN_TTL_MILLIS

    /** A later probe result only ever makes the picture more certain, never less - a confirmed
     * [CastCompatibilityVerdict.IncompatibleVideo] from an earlier probe must never be silently
     * replaced by a later, less decisive one (a flaky, momentarily-clean re-probe of the same
     * broken stream), but anything can be replaced by a *more* decisive verdict (see [rank]). */
    fun strongerOf(previous: CastCompatibilityVerdict?, candidate: CastCompatibilityVerdict): CastCompatibilityVerdict {
        if (previous == null || rank(candidate) >= rank(previous)) return candidate
        return previous
    }

    private fun rank(verdict: CastCompatibilityVerdict): Int = when (verdict) {
        is CastCompatibilityVerdict.IncompatibleVideo -> 2
        CastCompatibilityVerdict.Compatible, is CastCompatibilityVerdict.LikelyCompatible -> 1
        CastCompatibilityVerdict.Unknown -> 0
    }
}
