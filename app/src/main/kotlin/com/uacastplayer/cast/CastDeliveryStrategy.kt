package com.uacastplayer.cast

sealed class CastDeliveryMode {
    data object Direct : CastDeliveryMode()
    data object Proxy : CastDeliveryMode()
}

/**
 * What to do the moment [com.uacastplayer.data.cast.TsFirstSegmentDiagnostic] resolves - see
 * [CastDeliveryStrategy.onDiagnosticResult] and docs/CAST_PLAYBACK_RULES.md's routing table.
 */
sealed class CastRouteDecision {
    /** The codec is confirmed incompatible - remuxing the container never fixes a codec problem
     * (see [com.uacastplayer.proxy.RawTsRemuxActivation]'s own doc), so there is nothing left to
     * try; report [verdict] and stop touching the receiver for this attempt. */
    data class Blocked(val verdict: CastCompatibilityVerdict.IncompatibleVideo) : CastRouteDecision()

    /** A receiver never plays a bare MPEG-TS URL directly (it needs HLS/DASH wrapping), so a
     * direct-mode attempt on confirmed-compatible raw TS is a guaranteed 4s watchdog wait for
     * nothing - skip straight to the proxy, which remuxes it into playable HLS. */
    data object ProxyImmediately : CastRouteDecision()

    /** Nothing to act on yet - let the existing direct-then-watchdog flow continue unchanged. */
    data object NoAction : CastRouteDecision()
}

/**
 * Decides direct-vs-proxy delivery. Direct is always tried first unless the stream+receiver pair
 * is already known to be incompatible; once on Proxy there's nowhere further to fall back to, so
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
