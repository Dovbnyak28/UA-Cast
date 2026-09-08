package com.uacastplayer.core.cast

/**
 * Delivery path selected for a Chromecast playback attempt.
 *
 * This dependency-free domain value is shared by the Cast runtime, proxy adapter and diagnostics
 * counters without making any of those sibling components depend on another's implementation.
 */
enum class CastRouteKind { DIRECT, PROXY_REMUX, PROXY_REWRITE }
