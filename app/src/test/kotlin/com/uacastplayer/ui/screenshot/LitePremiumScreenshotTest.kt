package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.premium.billing.BillingProduct
import com.uacastplayer.premium.billing.PremiumProducts
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.premium.PremiumContent
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class LitePremiumScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val product = BillingProduct(PremiumProducts.LIFETIME, LicenseTier.LIFETIME, "Premium", "299,00 ₴")

    private fun capture(name: String, license: License, hasStore: Boolean = true, width: Int = 411) {
        val section = PremiumSectionState(
            entitlements = Entitlements.of(license, nowMillis = 0L),
            products = if (hasStore) listOf(product) else emptyList(),
            onPurchase = {},
            onRestore = {},
        )
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.size(width.dp, 700.dp).background(UaTheme.palette.void).padding(16.dp)) {
                    PremiumContent(section)
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun liteOffersOnePurchase() = capture("premium_lite", License.FREE)

    @Test @Config(qualifiers = "uk-w320dp-h891dp-xhdpi")
    fun liteOnANarrowPhone() = capture("premium_lite_narrow", License.FREE, width = 320)

    @Test @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun premiumHasNoRepeatPurchase() = capture("premium_owned", License(LicenseTier.LIFETIME))

    @Test @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun liteWithoutAStoreExplainsAvailability() = capture("premium_no_store", License.FREE, hasStore = false)
}
