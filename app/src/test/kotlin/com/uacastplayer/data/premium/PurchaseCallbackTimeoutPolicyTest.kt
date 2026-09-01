package com.uacastplayer.data.premium

import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseCallbackTimeoutPolicyTest {

    @Test
    fun unresolvedCallbackBecomesUnavailable() = runBlocking {
        val result = PurchaseCallbackTimeoutPolicy.await(
            deferred = CompletableDeferred(),
            timeoutMillis = 1,
        )

        assertEquals(PurchaseResult.Unavailable, result)
    }

    @Test
    fun completedCallbackWinsBeforeTimeout() = runBlocking {
        val deferred = CompletableDeferred<PurchaseResult>()
        val expected = PurchaseResult.Cancelled
        deferred.complete(expected)

        assertEquals(expected, PurchaseCallbackTimeoutPolicy.await(deferred, timeoutMillis = 1))
    }
}
