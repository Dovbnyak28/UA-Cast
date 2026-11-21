package com.uacastplayer.premium

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureManagerTest {
    private val now = 1_800_000_000_000L

    private fun managerFor(license: License): Pair<FeatureManager, MutableStateFlow<Entitlements>> {
        val flow = MutableStateFlow(Entitlements.of(license, now))
        return FeatureManager(flow) to flow
    }

    @Test fun liteAccessDoesNotDependOnStoreAvailability() {
        val (manager, _) = managerFor(License.FREE)
        for (feature in Feature.entries) {
            assertEquals(feature.name, FeaturePolicy.isFree(feature), manager.isUnlocked(feature))
        }
    }

    @Test fun anUnpaidInstallCanCastButCannotAddASecondPlaylist() {
        val (manager, _) = managerFor(License.FREE)
        assertTrue(manager.isUnlocked(Feature.CHROMECAST))
        assertTrue(manager.isUnlocked(Feature.RAW_TS_REMUX))
        assertTrue(manager.isUnlocked(Feature.PIP))
        assertTrue(manager.isUnlocked(Feature.THEMES))
        assertFalse(manager.isUnlocked(Feature.MULTI_PLAYLIST))
        assertTrue(manager.isLocked(Feature.DLNA))
    }

    @Test fun onePremiumPurchaseUnlocksEveryFeature() {
        val (manager, _) = managerFor(License(LicenseTier.LIFETIME))
        for (feature in Feature.entries) assertTrue(feature.name, manager.isUnlocked(feature))
    }

    @Test fun retiredUnpaidTiersCannotUnlockPremium() {
        for (tier in listOf(LicenseTier.TRIAL, LicenseTier.BETA, LicenseTier.ADMIN)) {
            val (manager, _) = managerFor(License(tier, expiresAtMillis = now + 1_000))
            assertEquals(Entitlements.FREE, manager.entitlements.value)
            assertFalse(manager.isUnlocked(Feature.DLNA))
        }
    }

    @Test fun legacyPaidAccessUsesTheSamePremiumModeUntilItsOriginalExpiry() {
        val expiry = now + 1_000
        for (tier in listOf(LicenseTier.MONTHLY, LicenseTier.YEARLY)) {
            val legacy = License(tier, expiry, "legacy")
            val (manager, _) = managerFor(legacy)
            assertEquals(LicenseTier.LIFETIME, manager.entitlements.value.effectiveTier)
            assertEquals(expiry, manager.entitlements.value.license.expiresAtMillis)
            assertTrue(manager.isUnlocked(Feature.DLNA))
            val expired = FeatureManager(MutableStateFlow(Entitlements.of(legacy, expiry)))
            assertFalse(expired.isUnlocked(Feature.DLNA))
            assertTrue(expired.entitlements.value.hasLapsed)
        }
    }

    @Test fun purchaseAndRefundChangeAnAlreadyOpenScreenImmediately() {
        val (manager, flow) = managerFor(License.FREE)
        assertFalse(manager.isUnlocked(Feature.DLNA))
        flow.value = Entitlements.of(License(LicenseTier.LIFETIME), now)
        assertTrue(manager.isUnlocked(Feature.DLNA))
        flow.value = Entitlements.FREE
        assertFalse(manager.isUnlocked(Feature.DLNA))
    }

    @Test fun isLockedIsExactlyTheOppositeOfIsUnlocked() {
        val (manager, _) = managerFor(License.FREE)
        for (feature in Feature.entries) {
            assertEquals(feature.name, !manager.isUnlocked(feature), manager.isLocked(feature))
        }
    }

    @Test fun theDefaultEntitlementsGrantOnlyLiteFeatures() {
        assertEquals(FeaturePolicy.featuresFor(LicenseTier.FREE), Entitlements.FREE.unlocked)
        assertFalse(Entitlements.FREE.hasLapsed)
    }
}
