package com.uacastplayer.diagnostics

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.uacastplayer.core.cast.CastRouteKind

/**
 * Persists [RemuxEffectivenessCounts] across app restarts - see [RemuxEffectivenessPolicy] for the
 * actual counting logic, kept here purely as I/O glue so the policy stays unit-testable without
 * Android. Local-only: nothing here is ever sent anywhere except as part of a user-initiated
 * diagnostics report.
 *
 * [recordProxyRouteAttemptOnce] separately dedupes by playback-attempt + resourceId + route: an
 * ordinary (non-remuxed) HLS
 * proxy resource is re-classified on every live-manifest poll from the receiver (see
 * `ProxyServer.fetchAndServeUpstreamResource`, which has no "already decided" shortcut for that
 * case the way an active remux session does), so counting on every call there would inflate
 * PROXY_REWRITE's attempted count by roughly one per manifest poll instead of once per channel.
 * Resource ids themselves are stable SHA fingerprints and are deliberately reused when the same
 * channel is played again, so they cannot be the whole key: doing that produced impossible reports
 * such as `1 attempted / 2 reached PLAYING`.
 */
class RemuxEffectivenessStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val attemptTracker = ProxyRouteAttemptTracker()

    fun record(route: CastRouteKind, outcome: CastRouteOutcome) {
        synchronized(lock) {
            persist(RemuxEffectivenessPolicy.apply(readCounts(), route, outcome))
        }
    }

    fun recordProxyRouteAttemptOnce(playbackAttemptId: Long, resourceId: String, route: CastRouteKind) {
        synchronized(lock) {
            if (!attemptTracker.markIfNew(playbackAttemptId, resourceId, route)) return
            persist(RemuxEffectivenessPolicy.apply(readCounts(), route, CastRouteOutcome.ATTEMPTED))
        }
    }

    /** Attempt keys only matter inside one Cast session. Clear them when it ends so a long-lived
     * process cannot retain one entry per channel playback forever. */
    fun resetAttemptTracking() {
        synchronized(lock) { attemptTracker.clear() }
    }

    fun snapshot(): RemuxEffectivenessCounts = synchronized(lock) { readCounts() }

    private fun readCounts() = RemuxEffectivenessCounts(
        directAttempted = prefs.getInt(KEY_DIRECT_ATTEMPTED, 0),
        directPlaying = prefs.getInt(KEY_DIRECT_PLAYING, 0),
        directFailed = prefs.getInt(KEY_DIRECT_FAILED, 0),
        remuxAttempted = prefs.getInt(KEY_REMUX_ATTEMPTED, 0),
        remuxPlaying = prefs.getInt(KEY_REMUX_PLAYING, 0),
        remuxFailed = prefs.getInt(KEY_REMUX_FAILED, 0),
        proxyRewriteAttempted = prefs.getInt(KEY_PROXY_REWRITE_ATTEMPTED, 0),
        proxyRewritePlaying = prefs.getInt(KEY_PROXY_REWRITE_PLAYING, 0),
        proxyRewriteFailed = prefs.getInt(KEY_PROXY_REWRITE_FAILED, 0),
    )

    private fun persist(counts: RemuxEffectivenessCounts) {
        prefs.edit {
            putInt(KEY_DIRECT_ATTEMPTED, counts.directAttempted)
            putInt(KEY_DIRECT_PLAYING, counts.directPlaying)
            putInt(KEY_DIRECT_FAILED, counts.directFailed)
            putInt(KEY_REMUX_ATTEMPTED, counts.remuxAttempted)
            putInt(KEY_REMUX_PLAYING, counts.remuxPlaying)
            putInt(KEY_REMUX_FAILED, counts.remuxFailed)
            putInt(KEY_PROXY_REWRITE_ATTEMPTED, counts.proxyRewriteAttempted)
            putInt(KEY_PROXY_REWRITE_PLAYING, counts.proxyRewritePlaying)
            putInt(KEY_PROXY_REWRITE_FAILED, counts.proxyRewriteFailed)
        }
    }

    companion object {
        @Volatile private var instance: RemuxEffectivenessStore? = null

        fun getInstance(context: Context): RemuxEffectivenessStore =
            instance ?: synchronized(this) {
                instance ?: RemuxEffectivenessStore(context).also { instance = it }
            }

        private const val PREFS_NAME = "remux_effectiveness_prefs"
        private const val KEY_DIRECT_ATTEMPTED = "direct_attempted"
        private const val KEY_DIRECT_PLAYING = "direct_playing"
        private const val KEY_DIRECT_FAILED = "direct_failed"
        private const val KEY_REMUX_ATTEMPTED = "remux_attempted"
        private const val KEY_REMUX_PLAYING = "remux_playing"
        private const val KEY_REMUX_FAILED = "remux_failed"
        private const val KEY_PROXY_REWRITE_ATTEMPTED = "proxy_rewrite_attempted"
        private const val KEY_PROXY_REWRITE_PLAYING = "proxy_rewrite_playing"
        private const val KEY_PROXY_REWRITE_FAILED = "proxy_rewrite_failed"
    }
}

internal data class ProxyRouteAttemptKey(
    val playbackAttemptId: Long,
    val resourceId: String,
    val route: CastRouteKind,
)

/** Pure, thread-safe de-duplication kept outside SharedPreferences so its invariants are unit tested. */
internal class ProxyRouteAttemptTracker {
    private val seen = HashSet<ProxyRouteAttemptKey>()

    @Synchronized
    fun markIfNew(playbackAttemptId: Long, resourceId: String, route: CastRouteKind): Boolean =
        seen.add(ProxyRouteAttemptKey(playbackAttemptId, resourceId, route))

    @Synchronized
    fun clear() = seen.clear()
}
