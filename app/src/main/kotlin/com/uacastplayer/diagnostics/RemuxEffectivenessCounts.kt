package com.uacastplayer.diagnostics

/** Which delivery path a channel load actually took - see `cast/CastSessionRepository` (Direct vs
 * Proxy) and `data/cast/ProxyServer` (within Proxy, whether the raw-TS remux engaged or the
 * origin was just passed through/rewritten as ordinary HLS). */
enum class CastRouteKind { DIRECT, PROXY_REMUX, PROXY_REWRITE }

/** [ATTEMPTED] fires once a route is committed to for a channel load; exactly one of
 * [REACHED_PLAYING] or [FAILED] follows for that same attempt - never both, never neither (a
 * channel switched away from mid-attempt, before either resolves, contributes an [ATTEMPTED] with
 * no matching outcome, which is expected and excluded from any success-rate math downstream). */
enum class CastRouteOutcome { ATTEMPTED, REACHED_PLAYING, FAILED }

data class RemuxEffectivenessCounts(
    val directAttempted: Int = 0,
    val directPlaying: Int = 0,
    val directFailed: Int = 0,
    val remuxAttempted: Int = 0,
    val remuxPlaying: Int = 0,
    val remuxFailed: Int = 0,
    val proxyRewriteAttempted: Int = 0,
    val proxyRewritePlaying: Int = 0,
    val proxyRewriteFailed: Int = 0,
)
