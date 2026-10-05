package com.uacastplayer.data.premium

import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.billing.PremiumProducts
import com.uacastplayer.premium.billing.PurchaseRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillingPurchaseSnapshotsTest {
    private val paid = PurchaseRecord("lifetime", LicenseTier.LIFETIME, 1, null, false)

    @Test fun `cold failed query is unknown not an authoritative empty result`() = runTest {
        val snapshots = BillingPurchaseSnapshots()
        assertNull(snapshots.state.value)
        assertNull(snapshots.refresh { null })
        assertNull(snapshots.state.value)
    }

    @Test fun `partial product type success cannot revoke or replace ownership`() = runTest {
        val snapshots = BillingPurchaseSnapshots()
        snapshots.refresh { listOf(paid) }
        assertEquals(setOf(paid), snapshots.state.value)
        assertNull(snapshots.refresh { type -> if (type == PremiumProducts.TYPE_SUBSCRIPTION) emptyList() else null })
        assertNull(snapshots.state.value)
        assertEquals(emptySet<PurchaseRecord>(), snapshots.refresh { emptyList() })
    }

    @Test fun `purchase callback invalidates an already running empty query`() = runTest {
        val snapshots = BillingPurchaseSnapshots()
        val gate = CompletableDeferred<Unit>()
        val refresh = launch {
            snapshots.refresh { gate.await(); emptyList() }
        }
        runCurrent()
        snapshots.notePurchases(listOf(paid))
        gate.complete(Unit)
        refresh.join()
        assertNull(snapshots.state.value)
        snapshots.refresh { listOf(paid) }
        assertEquals(setOf(paid), snapshots.state.value)
    }

    @Test fun `concurrent refresh calls serialize and cancellation does not publish empty`() = runTest {
        val snapshots = BillingPurchaseSnapshots()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val first = launch { snapshots.refresh { calls++; gate.await(); emptyList() } }
        val second = launch { snapshots.refresh { calls++; listOf(paid) } }
        runCurrent()
        assertEquals(1, calls)
        first.cancel()
        second.join()
        assertEquals(setOf(paid), snapshots.state.value)
    }
}
