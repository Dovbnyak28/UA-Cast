package com.uacastplayer.cast

import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsSourceKind

sealed interface CastDeliveryMode {
    data object Direct : CastDeliveryMode
    data object Proxy : CastDeliveryMode
}

/**
 * What to do with a current proxy observation or a previously cached observation - see
 * [CastDeliveryStrategy.onDiagnosticResult] and docs/CAST_PLAYBACK_RULES.md's routing table.
 */
sealed interface CastRouteDecision {
    /** The codec is confirmed incompatible - remuxing the container never fixes a codec problem
     * (see [com.uacastplayer.proxy.RawTsRemuxActivation]'s own doc), so there is nothing left to
     * try; report [verdict] and stop touching the receiver for this attempt. */
    data class Blocked(val verdict: CastCompatibilityVerdict.IncompatibleVideo) : CastRouteDecision

    /** The application's raw-TS policy uses proxy HLS wrapping rather than repeating the
     * direct watchdog wait. A cached compatible observation allows that choice before load. */
    data object ProxyImmediately : CastRouteDecision

    /** Nothing to act on yet - let the existing direct-then-watchdog flow continue unchanged. */
    data object NoAction : CastRouteDecision
}

/**
 * Decides direct-vs-proxy delivery. Direct is tried first unless cached source/codec information
 * or the stream+receiver's proven proxy history says otherwise; once on Proxy there's no further fallback, so
 * a subsequent failure there is terminal.
 */
object CastDeliveryStrategy {

    fun initialMode(isKnownIncompatible: Boolean): CastDeliveryMode =
        if (isKnownIncompatible) CastDeliveryMode.Proxy else CastDeliveryMode.Direct

    /** See docs/CAST_PLAYBACK_RULES.md for the full verdict x sourceKind routing table this
     * implements. [CastRouteDecision.Blocked] and [CastRouteDecision.ProxyImmediately] both act
     * immediately, even mid-watchdog-window - a Compatible+Hls or Unknown verdict is [NoAction]
     * and just lets the existing watchdog timeout decide, same as before this function existed. */
    fun onDiagnosticResult(verdict: CastCompatibilityVerdict, sourceKind: TsSourceKind): CastRouteDecision =
        when (verdict) {
            is CastCompatibilityVerdict.IncompatibleVideo -> CastRouteDecision.Blocked(verdict)
            CastCompatibilityVerdict.Compatible, is CastCompatibilityVerdict.LikelyCompatible ->
                if (sourceKind == TsSourceKind.RawTs) CastRouteDecision.ProxyImmediately else CastRouteDecision.NoAction
            CastCompatibilityVerdict.Unknown -> CastRouteDecision.NoAction
        }
}
