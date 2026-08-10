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
import com.uacastplayer.premium.billing.PurchaseResult
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
 * The premium surfaces, each composed and asserted.
 *
 * They are now reachable in the app - the unlock dialog opens from any of the seven gated controls,
 * and the section is in Settings - but they still cannot be *exercised*: everything below them is
 * decided by a store that only answers a build installed from Play. So these tests remain the only
 * thing standing between "the code compiles" and "the screen is correct" on the day it is switched
 * on, which is the day it is first seen by someone holding a card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PremiumSurfacesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = 1_800_000_000_000L

    private fun section(
        license: License,
        products: List<BillingProduct> = emptyList(),
        outcome: PurchaseResult? = null,
    ) = PremiumSectionState(
        entitlements = Entitlements.of(license, now),
        products = products,
        onPurchase = {},
        onRestore = {},
        lastOutcome = outcome,
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
        composeRule.onAllNodesWithContentDescriptionCount("Функція Premium", expected = 7)
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

    private val product = BillingProduct("monthly", LicenseTier.MONTHLY, "Місячна", "60,00 ₴")

    private fun showOutcome(outcome: PurchaseResult?) {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PremiumContent(section = section(License.FREE, listOf(product), outcome), nowMillis = now)
            }
        }
    }

    /**
     * The failure this whole outcome path exists for: a restore that reached Play and found nothing
     * must not be reported as a store that could not be reached.
     *
     * They are the same `Unavailable` in a naive mapping, and the difference is what the user does
     * next - one of them sends somebody to restart their router over an account that simply never
     * bought anything.
     */
    @Test
    fun anEmptyRestoreIsAnAnswerAndNotAConnectionProblem() {
        showOutcome(PurchaseResult.NothingToRestore)

        composeRule.onNodeWithText("На цьому акаунті Google немає що відновлювати.").assertIsDisplayed()
    }

    @Test
    fun anUnreachableStoreSaysSo() {
        showOutcome(PurchaseResult.Unavailable)

        composeRule.onNodeWithText(
            "Не вдалося звʼязатися з Google Play. Перевірте зʼєднання та спробуйте ще раз.",
        ).assertIsDisplayed()
    }

    /** Money: a purchase that did not go through has to say that nothing was charged, or the user's
     * next move is to try again and risk paying twice. */
    @Test
    fun aFailedPurchaseSaysNothingWasCharged() {
        showOutcome(PurchaseResult.Failed("card declined for account foo@example.com"))

        composeRule.onNodeWithText("Покупка не відбулася. Кошти не списано.").assertIsDisplayed()
        // Play's debug message can name the account or the product. It stays in the log.
        composeRule.onNodeWithText("card declined for account foo@example.com").assertDoesNotExist()
    }

    /**
     * Closing Play's sheet is a decision, not an error. An app that answers it with a message is
     * scolding the user for not buying, on the screen where it is asking to be trusted with money.
     */
    @Test
    fun cancellingSaysNothingAtAll() {
        showOutcome(PurchaseResult.Cancelled)

        composeRule.onNodeWithText("Покупка не відбулася. Кошти не списано.").assertDoesNotExist()
        composeRule.onNodeWithText("На цьому акаунті Google немає що відновлювати.").assertDoesNotExist()
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
