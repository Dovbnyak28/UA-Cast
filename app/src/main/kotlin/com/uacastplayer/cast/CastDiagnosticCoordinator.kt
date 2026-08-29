package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastCompatibilityPolicy
import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.DiagnosticCachePolicy
import com.uacastplayer.core.cast.TsSourceKind
import com.uacastplayer.data.cast.DiagnosticResultCache
import com.uacastplayer.data.cast.TsDiagnosticResult
import com.uacastplayer.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "CastDiagnosticCoordinator"

/**
 * Owns codec/source diagnostic warm-up and cache lifecycle without knowing anything about the Cast
 * SDK or playback state. The repository decides when to probe and how a verdict affects routing;
 * this class only debounces, cancels, rejects stale results and merges cache entries.
 */
internal class CastDiagnosticCoordinator(
    private val scope: CoroutineScope,
    private val activeStreamUrl: () -> String?,
    private val diagnose: suspend (String) -> TsDiagnosticResult,
    private val cache: DiagnosticResultCache = DiagnosticResultCache(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var warmupJob: Job? = null

    /** Replaces any pending warm-up. A still-valid cached verdict needs no network work. */
    fun scheduleWarmup(streamUrl: String) {
        cancelWarmup()
        if (cached(streamUrl) != null) return
        warmupJob = scope.launch {
            delay(DiagnosticCachePolicy.DEBOUNCE_MILLIS)
            val result = probe(streamUrl) ?: return@launch
            AppLog.d(TAG) { "warm-up cached verdict=${result.first} source=${result.second}" }
        }
    }

    fun cancelWarmup() {
        warmupJob?.cancel()
        warmupJob = null
    }

    fun cached(streamUrl: String) = cache.get(streamUrl, nowMillis())

    /**
     * Diagnoses and caches [streamUrl], unless the active channel changed while the suspend probe
     * was in flight. Returns the merged verdict actually stored plus the newly observed source.
     */
    suspend fun probe(streamUrl: String): Pair<CastCompatibilityVerdict, TsSourceKind>? {
        val result = diagnose(streamUrl)
        if (!StaleChannelGuard.isCurrent(streamUrl, activeStreamUrl())) {
            AppLog.d(TAG) { "stale diagnostic result ignored, channel no longer active" }
            return null
        }
        val verdict = CastCompatibilityPolicy.classify(result.programInfo)
        val merged = cache.merge(streamUrl, verdict, result.sourceKind, nowMillis())
        return merged to result.sourceKind
    }
}
