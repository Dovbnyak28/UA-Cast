package com.uacastplayer.cast

/** A proxy-server lifecycle transition, as seen by whoever owns the process-keep-alive service. */
enum class ProxyLifecycleEvent { STARTED, STOPPED }

/** Commands for the foreground service that keeps the process (and the proxy it hosts) alive. */
sealed interface ProxyServiceCommand {
    data object StartForeground : ProxyServiceCommand
    data object StopForeground : ProxyServiceCommand
}

/**
 * Pure mapping from a proxy lifecycle event to the foreground-service command it implies.
 * [ProxyLifecycleEvent.STARTED] fires 1:1 with `ProxyServer.start()` (Proxy delivery mode
 * kicking in); [ProxyLifecycleEvent.STOPPED] fires 1:1 with `proxyServer.stop()`, which already
 * covers disconnect, receiver error, and receiver-finished (see
 * [CastSideEffect.CloseProxySession] / [CastReceiverStatusReducer]).
 */
object ProxySessionPolicy {
    fun commandFor(event: ProxyLifecycleEvent): ProxyServiceCommand = when (event) {
        ProxyLifecycleEvent.STARTED -> ProxyServiceCommand.StartForeground
        ProxyLifecycleEvent.STOPPED -> ProxyServiceCommand.StopForeground
    }
}
