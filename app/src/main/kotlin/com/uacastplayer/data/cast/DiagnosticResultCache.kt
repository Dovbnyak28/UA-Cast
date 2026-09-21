package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.CachedDiagnostic
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.DiagnosticCachePolicy
import com.uacastplayer.core.cast.TsSourceKind

private const val INITIAL_CAPACITY = 16
private const val LOAD_FACTOR = 0.75f

/** LRU-bounded (see [DiagnosticCachePolicy.MAX_ENTRIES]) cache of diagnosed codec verdicts, keyed
 * by stream URL - see `cast/CastSessionRepository`'s diagnostic warm-up and watchdog-race wiring.
 * A hand-rolled `LinkedHashMap`-based LRU (same approach as `ProxyServer`'s resource table)
 * rather than `android.util.LruCache`, so this stays plain-JUnit testable. */
class DiagnosticResultCache {
    private val entries = object : LinkedHashMap<String, CachedDiagnostic>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDiagnostic>?): Boolean =
            size > DiagnosticCachePolicy.MAX_ENTRIES
    }
    private val lock = Any()

    /** An expired entry (see [DiagnosticCachePolicy.isValid]) is treated the same as no entry -
     * evicted lazily on the next [merge] for that key rather than proactively. */
    fun get(streamUrl: String, nowMillis: Long): CachedDiagnostic? {
        val entry = synchronized(lock) { entries[streamUrl] } ?: return null
        return entry.takeIf { DiagnosticCachePolicy.isValid(it, nowMillis) }
    }

    /** Merges [verdict] with whatever's already cached via [DiagnosticCachePolicy.strongerOf] -
     * never lets a later, less decisive probe quietly downgrade an already-confirmed verdict - and
     * returns the merged verdict actually stored. */
    fun merge(
        streamUrl: String,
        verdict: CastCompatibilityVerdict,
        sourceKind: TsSourceKind,
        nowMillis: Long,
    ): CastCompatibilityVerdict = synchronized(lock) {
        val merged = DiagnosticCachePolicy.strongerOf(entries[streamUrl]?.verdict, verdict)
        entries[streamUrl] = CachedDiagnostic(merged, sourceKind, nowMillis)
        merged
    }
}
