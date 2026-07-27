package com.uacastplayer.diagnostics

/** Pure counting logic for [RemuxEffectivenessCounts] - no I/O, no persistence, so it can be
 * exhaustively unit-tested on its own. [RemuxEffectivenessStore] is the thin Android glue that
 * calls this and persists the result. */
object RemuxEffectivenessPolicy {

    fun apply(
        counts: RemuxEffectivenessCounts,
        route: CastRouteKind,
        outcome: CastRouteOutcome,
    ): RemuxEffectivenessCounts =
        when (route) {
            CastRouteKind.DIRECT -> when (outcome) {
                CastRouteOutcome.ATTEMPTED -> counts.copy(directAttempted = counts.directAttempted + 1)
                CastRouteOutcome.REACHED_PLAYING -> counts.copy(directPlaying = counts.directPlaying + 1)
                CastRouteOutcome.FAILED -> counts.copy(directFailed = counts.directFailed + 1)
            }
            CastRouteKind.PROXY_REMUX -> when (outcome) {
                CastRouteOutcome.ATTEMPTED -> counts.copy(remuxAttempted = counts.remuxAttempted + 1)
                CastRouteOutcome.REACHED_PLAYING -> counts.copy(remuxPlaying = counts.remuxPlaying + 1)
                CastRouteOutcome.FAILED -> counts.copy(remuxFailed = counts.remuxFailed + 1)
            }
            CastRouteKind.PROXY_REWRITE -> when (outcome) {
                CastRouteOutcome.ATTEMPTED -> counts.copy(proxyRewriteAttempted = counts.proxyRewriteAttempted + 1)
                CastRouteOutcome.REACHED_PLAYING -> counts.copy(proxyRewritePlaying = counts.proxyRewritePlaying + 1)
                CastRouteOutcome.FAILED -> counts.copy(proxyRewriteFailed = counts.proxyRewriteFailed + 1)
            }
        }
}
