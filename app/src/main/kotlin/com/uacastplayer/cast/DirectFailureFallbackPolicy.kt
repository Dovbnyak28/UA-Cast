package com.uacastplayer.cast

/**
 * Whether a load that the Cast SDK reported as failed should be retried on the local proxy instead
 * of going through [CastLoadResultReducer]'s normal failure path (which tears the cast down and
 * hands playback back to the phone).
 *
 * This is the second of the two ways a channel ends up on the proxy, and the two are easy to
 * confuse. [CastStallWatchdogPolicy] covers the *silent* case: the receiver accepted the load and
 * then never played it. This one covers the *loud* case: the receiver rejected the load outright.
 * Both end in the same place, but only this one has an SDK error to go on.
 *
 * Two conditions narrow it, and neither is obvious from the call site:
 *
 * - **Only in [CastDeliveryMode.Direct].** A failure that already came from the proxy has nowhere
 *   left to fall back to; retrying would loop.
 * - **Only when no codec incompatibility has been confirmed.** The proxy remuxes containers, not
 *   video - it cannot turn MPEG-2 into something a Chromecast decodes. Once
 *   [com.uacastplayer.core.cast.CastCompatibilityVerdict.IncompatibleVideo] has been established
 *   for this stream the proxy
 *   attempt is known to be futile before it starts, and spending a load on it only delays telling
 *   the user. Note the diagnostic may well resolve *after* the failure, which is why this reads the
 *   confirmed verdict rather than assuming the ordering.
 *
 * A success never falls back, which is the case worth stating explicitly: `Success` here means the
 * receiver accepted the media, not that it is playing it, and it is precisely that gap the stall
 * watchdog exists to cover.
 */
object DirectFailureFallbackPolicy {

    fun shouldRetryOnProxy(
        result: CastLoadResult,
        mode: CastDeliveryMode,
        isConfirmedIncompatible: Boolean,
    ): Boolean = result is CastLoadResult.Failure &&
        mode == CastDeliveryMode.Direct &&
        !isConfirmedIncompatible
}
