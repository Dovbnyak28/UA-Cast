package com.uacastplayer.ads

import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerAdPolicyTest {
    private val enabled = BannerAdConfiguration(AdPlacement.entries.toSet(), AdRequestPermission.ALLOWED)
    private val lite = BannerAdAudience(Entitlements.FREE)

    @Test fun shippingDefaultDisablesEveryPlacement() {
        AdPlacement.entries.forEach { assertFalse(allowed(it, BannerAdConfiguration())) }
    }

    @Test fun liteCanUseOnlyExplicitlyEnabledPlacements() {
        assertTrue(allowed(AdPlacement.HOME_BANNER))
        assertTrue(allowed(AdPlacement.CHANNELS_BANNER))
        val homeOnly = enabled.copy(enabledPlacements = setOf(AdPlacement.HOME_BANNER))
        assertTrue(allowed(AdPlacement.HOME_BANNER, homeOnly))
        assertFalse(allowed(AdPlacement.CHANNELS_BANNER, homeOnly))
    }

    @Test fun everyActivePaidTierIsAdFreeIncludingCachedLegacyPurchases() {
        LicenseTier.entries.filter { it.isPaid }.forEach { tier ->
            val paid = BannerAdAudience(Entitlements.of(License(tier), 0L))
            AdPlacement.entries.forEach { assertFalse(allowed(it, audience = paid)) }
        }
    }

    @Test fun expiredLegacyPaidAccessUsesTheExistingLiteResolution() {
        val expired = BannerAdAudience(Entitlements.of(License(LicenseTier.YEARLY, 1L), 1L))
        assertTrue(expired.entitlements!!.hasLapsed)
        assertTrue(allowed(AdPlacement.HOME_BANNER, audience = expired))
    }

    @Test fun missingEntitlementNeverStartsAnAdRequest() {
        assertFalse(allowed(AdPlacement.HOME_BANNER, audience = BannerAdAudience()))
    }

    @Test fun unknownOrDeniedRequestPermissionFailsClosed() {
        listOf(AdRequestPermission.UNKNOWN, AdRequestPermission.DENIED).forEach { permission ->
            assertFalse(allowed(AdPlacement.HOME_BANNER, enabled.copy(requestPermission = permission)))
        }
    }

    @Test fun playbackGuidanceAndBackgroundSuppressRequests() {
        assertFalse(allowed(AdPlacement.HOME_BANNER, audience = lite.copy(blocked = true)))
        assertFalse(BannerAdPolicy.permitsRequests(AdPlacement.HOME_BANNER, enabled, lite,
            foreground = false, television = false))
    }

    @Test fun televisionNeverUsesPhoneBannerPlacements() {
        assertFalse(BannerAdPolicy.permitsRequests(AdPlacement.HOME_BANNER, enabled, lite,
            foreground = true, television = true))
    }

    private fun allowed(placement: AdPlacement, configuration: BannerAdConfiguration = enabled,
        audience: BannerAdAudience = lite): Boolean =
        BannerAdPolicy.permitsRequests(placement, configuration, audience, foreground = true, television = false)
}
