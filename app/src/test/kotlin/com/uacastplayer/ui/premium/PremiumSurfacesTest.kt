package com.uacastplayer.ui.premium

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.Feature
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.testing.RequiresComposeTestManifest
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
 * The premium surfaces that have **no production call site yet**, because nothing in the app is
 * gated: the unlock dialog is opened by tapping a locked control, and there are none.
 *
 * That makes these tests the only thing standing between "the code compiles" and "the screen is
 * correct". Shipping a surface nobody has ever rendered is how a paywall turns out to be broken on
 * the day it is switched on - so each one is composed here, and its one job is asserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PremiumSurfacesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = 1_800_000_000_000L

    private fun section(license: License, products: List<BillingProduct> = emptyList()) =
        PremiumSectionState(
            entitlements = Entitlements.of(license, now),
            products = products,
            onPurchase = {},
            onRestore = {},
        )

    @Test
    fun theUnlockDialogNamesTheFeatureThatWasTapped() {
        var seen = 0
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UnlockDialog(feature = Feature.DLNA, onSeePremium = { seen++ }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Трансляція на телевізор через DLNA — функція Premium").assertIsDisplayed()
        composeRule.onNodeWithText("Подивитись Premium").performClick()
        assertEquals(1, seen)
    }

    /**
     * A reserved [Feature] - one of the entries that exist so the name is fixed but that nothing
     * implements - has no user-facing name. The dialog must render nothing at all rather than a
     * title with a blank in it.
     */
    @Test
    fun theUnlockDialogStaysAwayForAFeatureWithNoName() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                UnlockDialog(feature = Feature.CLOUD_SYNC, onSeePremium = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Подивитись Premium").assertDoesNotExist()
    }

    /** Free tier: the sold features carry the lock badge, and the badge is what tells a screen
     * reader they are locked - the row's text alone cannot. */
    @Test
    fun lockedFeaturesCarryTheBadgeOnTheFreeTier() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PremiumContent(section = section(License.FREE), nowMillis = now)
            }
        }

        composeRule.onNodeWithText("Безкоштовна версія").assertIsDisplayed()
        composeRule.onNodeWithText("Трансляція на телевізор через DLNA").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescriptionCount("Функція Premium", expected = 8)
    }

    /** During the trial every sold feature is unlocked, so no badge should be drawn at all. */
    @Test
    fun nothingIsBadgedDuringTheTrial() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PremiumContent(section = section(License.trialStartingAt(now)), nowMillis = now)
            }
        }

        composeRule.onNodeWithContentDescription("Функція Premium").assertDoesNotExist()
    }

    /** With no store there is nothing to buy and nothing to restore - and the button that could
     * only ever do nothing must be absent, not merely inert. */
    @Test
    fun theRestoreButtonIsAbsentWhenThereIsNoStore() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PremiumContent(section = section(License.FREE), nowMillis = now)
            }
        }

        val noStore = "Купувати поки нічого — застосунок не опубліковано в магазині. " +
            "На час пробного періоду Premium відкритий усім."
        composeRule.onNodeWithText(noStore).assertIsDisplayed()
        composeRule.onNodeWithText("Відновити покупки").assertDoesNotExist()
    }

    /** And with a store, both the price and the restore path appear. The price comes from the
     * product, never from a string resource. */
    @Test
    fun aStoreBringsItsOwnPricesAndTheRestorePath() {
        val product = BillingProduct("monthly", LicenseTier.MONTHLY, "Місячна", "60,00 ₴")
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PremiumContent(section = section(License.FREE, listOf(product)), nowMillis = now)
            }
        }

        composeRule.onNodeWithText("Місячна").assertIsDisplayed()
        composeRule.onNodeWithText("60,00 ₴").assertIsDisplayed()
        composeRule.onNodeWithText("Відновити покупки").assertIsDisplayed()
    }
}

/** Asserts how many nodes carry [description] - `onAllNodesWithContentDescription` has no direct
 * count assertion, and "eight locked features each badged once" is the claim worth making. */
private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithContentDescriptionCount(
    description: String,
    expected: Int,
) {
    val count = onAllNodes(
        androidx.compose.ui.test.hasContentDescription(description),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size
    assertEquals("badges carrying \"$description\"", expected, count)
}
