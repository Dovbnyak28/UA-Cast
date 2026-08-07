package com.uacastplayer.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The business model, asserted.
 *
 * These tests are meant to fail when someone moves a feature between free and paid - that is the
 * point of them. A failure here is not a bug, it is the table telling you that you changed what
 * the app sells, and asking you to say so out loud.
 */
class FeaturePolicyTest {

    /** The approved boundary: an unpaid install is a working player, including casting. */
    @Test
    fun anUnpaidInstallGetsAWorkingPlayerIncludingChromecast() {
        val free = FeaturePolicy.featuresFor(LicenseTier.FREE)

        assertEquals(setOf(Feature.CHROMECAST, Feature.PIP, Feature.THEMES), free)
    }

    @Test
    fun theFeaturesThatAreSoldAreLockedForFree() {
        val free = FeaturePolicy.featuresFor(LicenseTier.FREE)

        assertFalse(Feature.MULTI_PLAYLIST in free)
        assertFalse(Feature.DLNA in free)
        assertFalse(Feature.PARENTAL_CONTROL in free)
        assertFalse(Feature.BACKUP in free)
        assertFalse(Feature.XTREAM in free)
        assertFalse(Feature.CUSTOM_EPG_SOURCE in free)
        assertFalse(Feature.CUSTOM_ICON_SOURCES in free)
        assertFalse(Feature.RAW_TS_REMUX in free)
    }

    /** A trial that hides what is being sold does not sell it. */
    @Test
    fun theTrialShowsEverything() {
        assertEquals(Feature.entries.toSet(), FeaturePolicy.featuresFor(LicenseTier.TRIAL))
    }

    @Test
    fun everyPaidTierUnlocksEverything() {
        for (tier in listOf(LicenseTier.MONTHLY, LicenseTier.YEARLY, LicenseTier.LIFETIME)) {
            assertEquals("$tier should unlock everything", Feature.entries.toSet(), FeaturePolicy.featuresFor(tier))
        }
    }

    @Test
    fun testerAndDeveloperTiersUnlockEverything() {
        assertEquals(Feature.entries.toSet(), FeaturePolicy.featuresFor(LicenseTier.BETA))
        assertEquals(Feature.entries.toSet(), FeaturePolicy.featuresFor(LicenseTier.ADMIN))
    }

    /** Every tier has to be answerable - a `when` that grew a gap would otherwise fail at runtime
     * on whichever tier was forgotten. */
    @Test
    fun everyTierHasAnAnswer() {
        for (tier in LicenseTier.entries) {
            FeaturePolicy.featuresFor(tier)
        }
    }

    @Test
    fun isFreeAgreesWithTheFreeTiersOwnSet() {
        val free = FeaturePolicy.featuresFor(LicenseTier.FREE)
        for (feature in Feature.entries) {
            assertEquals(feature.name, feature in free, FeaturePolicy.isFree(feature))
        }
    }

    /** Reserved names must stay locked until something implements them: a flag that reads "unlocked"
     * for a feature with no code behind it is how a paywall accidentally advertises nothing. */
    @Test
    fun reservedFeaturesAreNotFree() {
        val reserved = listOf(
            Feature.CLOUD_SYNC, Feature.SMART_SEARCH, Feature.RECORDING,
            Feature.NAS, Feature.SMB, Feature.WEBDAV,
            Feature.PLEX, Feature.JELLYFIN, Feature.EMBY,
        )
        for (feature in reserved) {
            assertTrue("$feature must not be free", !FeaturePolicy.isFree(feature))
        }
    }
}
