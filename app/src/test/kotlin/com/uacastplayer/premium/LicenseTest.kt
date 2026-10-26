package com.uacastplayer.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseTest {

    private val now = 1_800_000_000_000L

    @Test
    fun freeNeverExpires() {
        val free = License.FREE
        assertTrue(free.isActive(now))
        assertTrue(free.isActive(now + 100L * 365 * 24 * 60 * 60 * 1000))
        assertFalse(free.hasLapsed(now))
    }

    @Test
    fun lifetimeHasNoExpiryAndSoNeverLapses() {
        val lifetime = License(LicenseTier.LIFETIME, expiresAtMillis = null, source = "lifetime")
        assertTrue(lifetime.isActive(now))
        assertEquals(LicenseTier.LIFETIME, lifetime.effectiveTier(now))
        assertFalse(lifetime.hasLapsed(now))
    }

    @Test
    fun aSubscriptionAppliesUpToItsExpiryAndNotAfter() {
        val monthly = License(LicenseTier.MONTHLY, expiresAtMillis = now + 1000)

        assertTrue(monthly.isActive(now))
        assertTrue(monthly.isActive(now + 999))
        assertFalse(monthly.isActive(now + 1000))
        assertFalse(monthly.isActive(now + 5000))
    }

    /**
     * The distinction the whole type exists for: an expired subscription governs access exactly as
     * FREE does, while still remembering that it was a subscription - so the app can say "your
     * subscription ended" instead of quietly showing fewer buttons.
     */
    @Test
    fun anExpiredSubscriptionActsFreeButIsStillDistinguishableFromNeverHavingPaid() {
        val expired = License(LicenseTier.MONTHLY, expiresAtMillis = now - 1)

        assertEquals(LicenseTier.FREE, expired.effectiveTier(now))
        assertTrue(expired.hasLapsed(now))
        assertEquals(LicenseTier.MONTHLY, expired.tier)

        assertFalse(License.FREE.hasLapsed(now))
    }

    @Test
    fun legacyPromotionalLicencesResolveToLite() {
        for (tier in listOf(LicenseTier.TRIAL, LicenseTier.BETA, LicenseTier.ADMIN)) {
            assertEquals(License.FREE, License(tier, now + 1000, "legacy").currentModel())
        }
    }

    @Test
    fun legacyPaidLicencesKeepTheirSourceAndExpiryWhenMigratedToPremium() {
        for (tier in listOf(LicenseTier.MONTHLY, LicenseTier.YEARLY)) {
            val legacy = License(tier, now + 1000, "legacy_$tier")
            assertEquals(legacy.copy(tier = LicenseTier.LIFETIME), legacy.currentModel())
            assertEquals(LicenseTier.FREE, legacy.currentModel().effectiveTier(now + 1000))
        }
    }

    @Test
    fun paidTiersIncludeCurrentPremiumAndTheTwoLegacyPurchaseTypes() {
        assertTrue(LicenseTier.MONTHLY.isPaid)
        assertTrue(LicenseTier.YEARLY.isPaid)
        assertTrue(LicenseTier.LIFETIME.isPaid)

        assertFalse(LicenseTier.FREE.isPaid)
        assertFalse(LicenseTier.TRIAL.isPaid)
        assertFalse(LicenseTier.BETA.isPaid)
        assertFalse(LicenseTier.ADMIN.isPaid)
    }
}
