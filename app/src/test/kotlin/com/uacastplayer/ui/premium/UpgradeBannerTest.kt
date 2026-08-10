package com.uacastplayer.ui.premium

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The upgrade banner is the only premium surface that can appear without being asked for, so the
 * tests that matter most are the ones asserting it stays away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class UpgradeBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun sectionFor(license: License) = PremiumSectionState(
        entitlements = Entitlements.of(license, now),
        products = emptyList(),
        onPurchase = {},
        onRestore = {},
    )

    private fun show(license: License) {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UpgradeBanner(section = sectionFor(license), nowMillis = now, onSeePremium = {})
            }
        }
    }

    /** Somebody who never had premium is not losing anything. An app that asks for money on its
     * home screen from day one is the app people uninstall. */
    @Test
    fun nothingIsShownToAFreeUser() {
        show(License.FREE)
        composeRule.onNodeWithTag(UiTestTags.UPGRADE_BANNER).assertDoesNotExist()
    }

    /** A countdown that starts on day one is nagging, not information. */
    @Test
    fun nothingIsShownEarlyInTheTrial() {
        show(License(LicenseTier.TRIAL, expiresAtMillis = now + 10 * day, source = "trial"))
        composeRule.onNodeWithTag(UiTestTags.UPGRADE_BANNER).assertDoesNotExist()
    }

    @Test
    fun theLastFewDaysOfTheTrialAreAnnounced() {
        show(License(LicenseTier.TRIAL, expiresAtMillis = now + 2 * day, source = "trial"))
        composeRule.onNodeWithTag(UiTestTags.UPGRADE_BANNER).assertIsDisplayed()
    }

    /** Saying so beats letting the user find out by tapping something that used to work. */
    @Test
    fun aLapsedSubscriptionIsAnnounced() {
        show(License(LicenseTier.MONTHLY, expiresAtMillis = now - day, source = "monthly"))
        composeRule.onNodeWithTag(UiTestTags.UPGRADE_BANNER).assertIsDisplayed()
    }

    @Test
    fun nothingIsShownWhilePremiumIsRunning() {
        show(License(LicenseTier.YEARLY, expiresAtMillis = now + 200 * day, source = "yearly"))
        composeRule.onNodeWithTag(UiTestTags.UPGRADE_BANNER).assertDoesNotExist()
    }

    @Test
    fun lifetimeIsNeverNagged() {
        show(License(LicenseTier.LIFETIME, expiresAtMillis = null, source = "lifetime"))
        composeRule.onNodeWithTag(UiTestTags.UPGRADE_BANNER).assertDoesNotExist()
    }

    /**
     * The banner is placed inline on Home, above the dashboard, with spacing of its own - so an
     * invisible banner that still measured as a gap would put dead space at the top of the first
     * screen of the app for everyone who is never shown it, which is almost everyone.
     *
     * Asserted by measuring rather than reasoned about: the host applies its padding to the banner's
     * own modifier, and whether `AnimatedVisibility` emits a node while hidden is an implementation
     * detail of Compose, not a promise.
     */
    @Test
    fun anInvisibleBannerTakesNoSpaceAtAll() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column(modifier = Modifier.testTag("host")) {
                    Box(modifier = Modifier.size(40.dp).testTag("spacer"))
                    UpgradeBanner(
                        section = sectionFor(License.FREE),
                        nowMillis = now,
                        onSeePremium = {},
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        val host = composeRule.onNodeWithTag("host").fetchSemanticsNode().size.height
        val spacer = composeRule.onNodeWithTag("spacer").fetchSemanticsNode().size.height
        assertEquals("the 12dp of host padding must not survive the banner being hidden", spacer, host)
    }

    /** Rounded up: "0 days left" on a trial that still has hours in it is a lie in the direction
     * that costs the user something. */
    @Test
    fun remainingDaysRoundUp() {
        val section = sectionFor(License(LicenseTier.TRIAL, expiresAtMillis = now + day + 1, source = "trial"))
        org.junit.Assert.assertEquals(2, section.daysRemaining(now))
    }
}
