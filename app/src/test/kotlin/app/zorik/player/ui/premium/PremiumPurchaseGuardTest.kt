package app.zorik.player.ui.premium

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.R
import app.zorik.player.premium.Entitlements
import app.zorik.player.premium.LicenseTier
import app.zorik.player.premium.PremiumSectionState
import app.zorik.player.premium.billing.BillingConnectionState
import app.zorik.player.premium.billing.BillingProduct
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Whether the buy controls can be pressed twice.
 *
 * They could, and the store layer has exactly one slot for an answer: Play reports a purchase
 * through a client-wide listener rather than through the call that started it, so a second attempt
 * launched before the first is answered leaves the first waiting for a reply now addressed to the
 * second. The window is not theoretical either - it covers the seconds while Play's own sheet is
 * opening, which the screen otherwise says nothing about at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PremiumPurchaseGuardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private val premium = BillingProduct(
        id = "premium_lifetime",
        tier = LicenseTier.LIFETIME,
        title = "Premium",
        formattedPrice = "49,00 ₴",
    )

    private var purchases = mutableListOf<BillingProduct>()
    private var restores = 0
    private val buyText get() = application.getString(R.string.premium_buy, premium.formattedPrice)

    private fun show(isPurchasing: Boolean) {
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                PremiumContent(
                    section = PremiumSectionState(
                        entitlements = Entitlements.FREE,
                        products = listOf(premium),
                        onPurchase = { purchases += it },
                        onRestore = { restores++ },
                        isPurchasing = isPurchasing,
                        connection = BillingConnectionState.CONNECTED,
                    ),
                )
            }
        }
    }

    /** The bug: a second press while the first is still with the store. */
    @Test
    fun `the buy button is dead while an attempt is with the store`() {
        show(isPurchasing = true)

        composeRule.onNodeWithText(buyText).assertIsNotEnabled()
        composeRule.onNodeWithText(buyText).performClick()

        assertEquals("a disabled button must not report a purchase", emptyList<BillingProduct>(), purchases)
    }

    /** Restore goes through the same one slot, so it is disabled by the same flag. */
    @Test
    fun `restore is dead for the same reason and at the same time`() {
        show(isPurchasing = true)

        composeRule.onNodeWithText(application.getString(R.string.premium_restore)).assertIsNotEnabled()
        assertEquals(0, restores)
    }

    /**
     * The control, and what stops the two above from being satisfied by a screen whose buttons never
     * work: with nothing in flight, both are live and report exactly one attempt per press.
     */
    @Test
    fun `both are live again once nothing is in flight`() {
        show(isPurchasing = false)

        composeRule.onNodeWithText(buyText).assertIsEnabled()
        composeRule.onNodeWithText(buyText).performClick()
        composeRule.onNodeWithText(application.getString(R.string.premium_restore)).assertIsEnabled()
        composeRule.onNodeWithText(application.getString(R.string.premium_restore)).performClick()

        assertEquals(listOf(premium), purchases)
        assertEquals(1, restores)
    }
}
