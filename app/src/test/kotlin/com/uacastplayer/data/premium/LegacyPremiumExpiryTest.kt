package com.uacastplayer.data.premium

import com.uacastplayer.premium.Feature
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseStorage
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.billing.PremiumProducts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LegacyPremiumExpiryTest {
    private class Storage(override var storedLicense: License?) : LicenseStorage {
        override var clockHighWaterMark = 0L
    }

    @Test fun legacyAccessExpiresWhileOpenWithoutAnExplicitRefresh() = runTest {
        val storage = Storage(License(LicenseTier.YEARLY, NOW + 1000, PremiumProducts.YEARLY))
        val repo = PremiumRepository(FakeBillingProvider(), storage, backgroundScope) {
            NOW + testScheduler.currentTime
        }
        repo.loadInitial()
        assertTrue(Feature.DLNA in repo.entitlements.value.unlocked)
        runCurrent()
        advanceTimeBy(1001)
        runCurrent()
        assertTrue(repo.entitlements.value.hasLapsed)
        assertEquals(LicenseTier.FREE, repo.entitlements.value.effectiveTier)
        assertFalse(Feature.DLNA in repo.entitlements.value.unlocked)
        assertEquals(NOW + 1000, storage.storedLicense?.expiresAtMillis)
    }

    @Test fun anOldExpiryCannotRevokeNewNonExpiringPremium() = runTest {
        val storage = Storage(License(LicenseTier.MONTHLY, NOW + 1000, PremiumProducts.MONTHLY))
        val repo = PremiumRepository(FakeBillingProvider(), storage, backgroundScope) {
            NOW + testScheduler.currentTime
        }
        repo.loadInitial()
        runCurrent()
        storage.storedLicense = License(LicenseTier.LIFETIME, source = PremiumProducts.LIFETIME)
        repo.refresh()
        advanceTimeBy(1001)
        runCurrent()
        assertEquals(LicenseTier.LIFETIME, repo.entitlements.value.effectiveTier)
        assertFalse(repo.entitlements.value.hasLapsed)
        assertTrue(Feature.DLNA in repo.entitlements.value.unlocked)
    }

    @Test fun ownerDestructionCancelsPendingExpiryWork() = runTest {
        val storage = Storage(License(LicenseTier.YEARLY, NOW + 1000, PremiumProducts.YEARLY))
        val owner = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        try {
            val repo = PremiumRepository(FakeBillingProvider(), storage, owner) { NOW + testScheduler.currentTime }
            repo.loadInitial()
            runCurrent()
            owner.cancel()
            advanceTimeBy(1001)
            runCurrent()
            assertEquals(LicenseTier.LIFETIME, repo.entitlements.value.effectiveTier)
            assertEquals(NOW, storage.clockHighWaterMark)
        } finally { owner.cancel() }
    }

    @Test fun wallClockRollbackDoesNotExtendLegacyAccess() = runTest {
        val storage = Storage(License(LicenseTier.YEARLY, NOW + 1000, PremiumProducts.YEARLY))
        var clock = NOW
        val repo = PremiumRepository(FakeBillingProvider(), storage, backgroundScope) { clock }
        repo.loadInitial()
        runCurrent()
        clock -= 60_000
        advanceTimeBy(1001)
        runCurrent()
        assertTrue(repo.entitlements.value.hasLapsed)
        assertEquals(NOW + 1000, storage.clockHighWaterMark)
    }

    @Test fun foregroundRefreshAfterClockRollbackCannotRestartThePaidTerm() = runTest {
        val storage = Storage(License(LicenseTier.YEARLY, NOW + 1000, PremiumProducts.YEARLY))
        var clock = NOW
        val repo = PremiumRepository(FakeBillingProvider(), storage, backgroundScope) { clock }
        repo.loadInitial()
        runCurrent()
        advanceTimeBy(500)
        clock -= 60_000
        repo.refresh()
        advanceTimeBy(501)
        runCurrent()
        assertTrue(repo.entitlements.value.hasLapsed)
    }

    private companion object { const val NOW = 1_800_000_000_000L }
}
