package app.zorik.player.ui.premium

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.zorik.player.premium.Entitlements
import app.zorik.player.premium.Feature
import app.zorik.player.premium.FeatureManager
import app.zorik.player.premium.License
import app.zorik.player.premium.LicenseTier
import app.zorik.player.premium.PremiumSectionState
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class FeatureGateReactivityTest {
    @get:Rule val rule = createComposeRule()
    private val premium = Entitlements.of(License(LicenseTier.LIFETIME), 0L)

    private fun render(state: MutableStateFlow<Entitlements>) {
        val manager = FeatureManager(state)
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                val entitlement by state.collectAsState()
                val section = PremiumSectionState(entitlement, emptyList(), {}, {})
                val surfaces = rememberFeatureGate(manager, section)
                CompositionLocalProvider(LocalFeatureGate provides surfaces.gate) {
                    GatedChild()
                }
            }
        }
    }

    @Test fun purchaseUpdatesAnAlreadyComposedChildWithoutNavigation() {
        val state = MutableStateFlow(Entitlements.FREE)
        render(state)
        rule.onNodeWithText("locked").assertExists()
        rule.runOnIdle { state.value = premium }
        rule.onNodeWithText("unlocked").assertExists()
        rule.onNodeWithText("locked").assertDoesNotExist()
    }

    @Test fun refundUpdatesAnAlreadyComposedChildWithoutNavigation() {
        val state = MutableStateFlow(premium)
        render(state)
        rule.onNodeWithText("unlocked").assertExists()
        rule.runOnIdle { state.value = Entitlements.FREE }
        rule.onNodeWithText("locked").assertExists()
        rule.onNodeWithText("unlocked").assertDoesNotExist()
    }
}

/** No changing parameters: only the provided gate can invalidate this child. */
@Composable
private fun GatedChild() {
    Text(if (LocalFeatureGate.current.isLocked(Feature.DLNA)) "locked" else "unlocked")
}
