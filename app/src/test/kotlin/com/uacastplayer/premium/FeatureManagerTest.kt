package com.uacastplayer.premium

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureManagerTest {

    private val now = 1_800_000_000_000L

    private fun managerFor(
        license: License,
        nowMillis: Long = now,
    ): Pair<FeatureManager, MutableStateFlow<Entitlements>> {
        val flow = MutableStateFlow(Entitlements.of(license, nowMillis))
        return FeatureManager(flow) to flow
    }

    @Test
    fun anUnpaidInstallCanCastButCannotAddASecondPlaylist() {
        val (manager, _) = managerFor(License.FREE)

        assertTrue(manager.isUnlocked(Feature.CHROMECAST))
        assertTrue(manager.isUnlocked(Feature.PIP))
        assertTrue(manager.isUnlocked(Feature.THEMES))

        assertFalse(manager.isUnlocked(Feature.MULTI_PLAYLIST))
        assertTrue(manager.isLocked(Feature.DLNA))
    }

    @Test
    fun aTrialUnlocksEverythingWhileItLasts() {
        val (manager, _) = managerFor(License.trialStartingAt(now))

        for (feature in Feature.entries) {
            assertTrue("$feature should be unlocked during the trial", manager.isUnlocked(feature))
        }
    }

    /** The bug this whole design is meant to make impossible: an expired entitlement still
     * unlocking things because one call site read `license.tier` instead of asking. */
    @Test
    fun anExpiredTrialUnlocksExactlyWhatFreeDoes() {
        val trial = License.trialStartingAt(now)
        val (manager, _) = managerFor(trial, nowMillis = now + License.TRIAL_DURATION_MILLIS)

        assertEquals(FeaturePolicy.featuresFor(LicenseTier.FREE), manager.entitlements.value.unlocked)
        assertFalse(manager.isUnlocked(Feature.DLNA))
        assertTrue(manager.isUnlocked(Feature.CHROMECAST))
        assertTrue(manager.entitlements.value.hasLapsed)
        assertEquals(LicenseTier.FREE, manager.entitlements.value.effectiveTier)
    }

    /** A purchase must be visible to screens that are already on screen, without anything being
     * told to refresh - which is why access is a flow rather than a function call. */
    @Test
    fun buyingWhileAScreenIsOpenChangesTheAnswerImmediately() {
        val (manager, flow) = managerFor(License.FREE)
        assertFalse(manager.isUnlocked(Feature.DLNA))

        flow.value = Entitlements.of(License(LicenseTier.YEARLY, expiresAtMillis = now + 1_000_000), now)

        assertTrue(manager.isUnlocked(Feature.DLNA))
    }

    @Test
    fun isLockedIsExactlyTheOppositeOfIsUnlocked() {
        val (manager, _) = managerFor(License.FREE)
        for (feature in Feature.entries) {
            assertEquals(feature.name, !manager.isUnlocked(feature), manager.isLocked(feature))
        }
    }

    @Test
    fun theDefaultEntitlementsGrantOnlyWhatFreeGrants() {
        assertEquals(FeaturePolicy.featuresFor(LicenseTier.FREE), Entitlements.FREE.unlocked)
        assertFalse(Entitlements.FREE.hasLapsed)
    }
}
