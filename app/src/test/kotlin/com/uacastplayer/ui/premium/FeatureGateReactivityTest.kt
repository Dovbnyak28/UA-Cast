package com.uacastplayer.ui.premium

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.Feature
import com.uacastplayer.premium.FeatureManager
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
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
            UaCastTheme(AppTheme.CINEMA) {
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
