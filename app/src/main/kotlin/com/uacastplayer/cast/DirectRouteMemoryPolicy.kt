package com.uacastplayer.cast

/**
 * Whether a channel that had to abandon its direct attempt is now *proven* to need the proxy, and
 * so worth remembering in `data/cast/IncompatibilityMemoryStore` (which
 * [CastDeliveryStrategy.initialMode] reads to skip direct entirely next time).
 *
 * Until this existed, that store was only ever written for a confirmed MPEG-2 verdict - the one
 * case [CastRecoveryPolicy] answers with `GiveUp` - so the overwhelmingly common reason a cast ends
 * up on the proxy, the direct watchdog simply timing out, was never remembered at all. A device
 * capture made the cost obvious: seven consecutive channel loads, seven identical
 * `Falling back to proxy: watchdog_timeout` lines, each preceded by a full 4 seconds of dead air on
 * a receiver that was never going to play that URL directly. The knowledge was thrown away every
 * time, including between two switches of the same channel a minute apart.
 *
 * The bar for remembering is deliberately "the proxy then reached PLAYING", not merely "direct
 * failed". A direct attempt can fail for reasons that have nothing to do with the route - the
 * origin was briefly down, the network dropped - and recording those would push a channel onto the
 * proxy for 30 days over one bad moment, spending the phone's bandwidth and battery to relay a
 * stream the receiver could have fetched itself. Proxy playback succeeding on the same stream
 * moments later rules all of that out: the stream is fine, the receiver is fine, the network is
 * fine, and the only thing that did not work is the direct route. That is a property of the pair,
 * and it is exactly what the store is for.
 *
 * The same reasoning as [IncompatibilityRecordingPolicy], applied to the other end of the attempt:
 * that one refuses to record a failure that followed successful playback, this one refuses to
 * record one that was not followed by it.
 */
object DirectRouteMemoryPolicy {

    fun provenProxyOnly(mode: CastDeliveryMode, status: ReceiverStatus): Boolean =
        mode == CastDeliveryMode.Proxy && status == ReceiverStatus.PLAYING
}
