package com.uacastplayer.cast

/**
 * The proxy foreground service can protect a Chromecast and a DLNA session at the same time.
 * Order matters only for the notification: the most recently started owner is the one displayed.
 */
internal data class CastProxyOwnership(
    val activeTargets: List<CastProxyTarget> = emptyList(),
) {
    val displayedTarget: CastProxyTarget? get() = activeTargets.lastOrNull()
}

/** Pure owner accounting kept out of the Android service so teardown races are unit-testable. */
internal object CastProxyOwnershipPolicy {
    fun started(state: CastProxyOwnership, target: CastProxyTarget): CastProxyOwnership =
        state.copy(activeTargets = state.activeTargets.filterNot { it == target } + target)

    fun stopped(state: CastProxyOwnership, target: CastProxyTarget): CastProxyOwnership =
        state.copy(activeTargets = state.activeTargets.filterNot { it == target })
}
