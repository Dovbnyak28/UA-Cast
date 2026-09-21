package com.uacastplayer.data.premium

import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bounds the one callback-based operation in Play Billing.
 *
 * The store reports a purchase through a process-wide listener, so a killed Activity, a Play
 * service restart, or an OEM interruption can otherwise leave the caller suspended forever. A
 * timeout is deliberately mapped to [PurchaseResult.Unavailable]: no purchase is claimed and a
 * later Play callback can still refresh the entitlement flow if it arrives.
 */
internal object PurchaseCallbackTimeoutPolicy {

    const val DEFAULT_TIMEOUT_MILLIS = 90_000L

    suspend fun await(
        deferred: Deferred<PurchaseResult>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): PurchaseResult = withTimeoutOrNull(timeoutMillis.coerceAtLeast(1L)) {
        deferred.await()
    } ?: PurchaseResult.Unavailable
}
