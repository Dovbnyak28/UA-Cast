package com.uacastplayer.data.premium

import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PendingPurchaseCoordinatorTest {
    @Test fun `timeout cannot let late A callback complete a newer checkout B`() = runTest {
        val coordinator = PendingPurchaseCoordinator()
        val first = coordinator.begin("a")!!
        assertEquals(PurchaseResult.Unavailable, PurchaseCallbackTimeoutPolicy.await(first.result, 10))
        assertNull(coordinator.begin("b"))
        coordinator.complete(emptyList()) { PurchaseResult.Cancelled }
        assertEquals(PurchaseResult.Cancelled, first.result.await())
        assertNotNull(coordinator.begin("b"))
    }

    @Test fun `synchronous launch rejection releases only its own attempt`() {
        val coordinator = PendingPurchaseCoordinator()
        val first = coordinator.begin("a")!!
        coordinator.launchFailed(first)
        val second = coordinator.begin("b")!!
        coordinator.launchFailed(first)
        assertNull(coordinator.begin("c"))
        coordinator.launchFailed(second)
        assertNotNull(coordinator.begin("c"))
    }
}
