package com.uacastplayer.ui.ads

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uacastplayer.ads.AdPlacement
import com.uacastplayer.ads.AdRequestPermission
import com.uacastplayer.ads.BannerAdAudience
import com.uacastplayer.ads.BannerAdConfiguration
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.tv.LocalTvMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class BannerAdSlotTest {
    @get:Rule val rule = createComposeRule()
    private val premium = Entitlements.of(License(LicenseTier.LIFETIME), 0L)
    private val renderer = CountingRenderer()
    private val currentRenderer = mutableStateOf(renderer)
    private val visible = mutableStateOf(true)
    private val audience = mutableStateOf(BannerAdAudience(Entitlements.FREE))
    private val configuration = mutableStateOf(
        BannerAdConfiguration(AdPlacement.entries.toSet(), AdRequestPermission.ALLOWED))
    private val television = mutableStateOf(false)
    private val owner = FixtureLifecycleOwner()

    @Test fun defaultIntegrationHasNoSpaceOrSideEffects() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalBannerAdAudience provides audience.value) {
                    Column {
                        Text("Before", Modifier.testTag("before"))
                        BannerAdSlot(AdPlacement.HOME_BANNER)
                        Text("After", Modifier.testTag("after"))
                    }
                }
            }
        }
        assertNoGap()
    }

    @Test fun cachedPremiumNeverMountsTheRenderer() {
        audience.value = BannerAdAudience(premium)
        show()
        rule.onNodeWithTag("ad").assertDoesNotExist()
        assertEquals(0, renderer.created)
        assertNoGap()
    }

    @Test fun purchaseDisposesTheExistingAdAndKeepsContent() {
        show()
        rule.onNodeWithTag("ad").assertExists()
        rule.runOnIdle { audience.value = BannerAdAudience(premium) }
        rule.onNodeWithTag("ad").assertDoesNotExist()
        rule.onNodeWithTag("after").assertExists()
        assertEquals(1, renderer.created)
        assertEquals(1, renderer.disposed)
        assertNoGap()
    }

    @Test fun refundUsesLatestEntitlementWithoutNavigation() {
        audience.value = BannerAdAudience(premium)
        show()
        rule.runOnIdle { audience.value = BannerAdAudience(Entitlements.FREE) }
        rule.onNodeWithTag("ad").assertExists()
        assertEquals(1, renderer.created)
    }

    @Test fun revokingRequestPermissionDisposesAndCollapsesTheSlot() {
        show()
        rule.runOnIdle {
            configuration.value = configuration.value.copy(requestPermission = AdRequestPermission.DENIED)
        }
        rule.onNodeWithTag("ad").assertDoesNotExist()
        assertEquals(1, renderer.disposed)
        assertNoGap()
    }

    @Test fun playbackSuspendsAndReturningMountsOnlyOneFreshAd() {
        show()
        rule.runOnIdle { audience.value = audience.value.copy(blocked = true) }
        rule.onNodeWithTag("ad").assertDoesNotExist()
        assertEquals(1, renderer.disposed)
        rule.runOnIdle { audience.value = audience.value.copy(blocked = false) }
        rule.onNodeWithTag("ad").assertExists()
        assertEquals(2, renderer.created)
    }

    @Test fun pauseAndStopDestroyTheAdBeforeResume() {
        show()
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.onNodeWithTag("ad").assertDoesNotExist()
        assertEquals(1, renderer.disposed)
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.onNodeWithTag("ad").assertDoesNotExist()
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.onNodeWithTag("ad").assertExists()
        assertEquals(2, renderer.created)
    }

    @Test fun televisionAndMissingEntitlementNeverMountAnAd() {
        television.value = true
        show()
        assertEquals(0, renderer.created)
        rule.runOnIdle { television.value = false; audience.value = BannerAdAudience() }
        rule.onNodeWithTag("ad").assertDoesNotExist()
        assertEquals(0, renderer.created)
    }

    @Test fun pendingOrNoFillRendererDoesNotLeaveEvenAnAdvertisingLabel() {
        renderer.hasFill = false
        show()
        assertEquals(1, renderer.created)
        rule.onNodeWithTag("ad_slot_HOME_BANNER").assertDoesNotExist()
        assertNoGap()
    }

    @Test fun replacingTheIntegrationDisposesTheOldRendererBeforeMountingAnother() {
        show()
        val replacement = CountingRenderer()
        rule.runOnIdle { currentRenderer.value = replacement }
        rule.onNodeWithTag("ad").assertExists()
        assertEquals(1, renderer.created)
        assertEquals(1, renderer.disposed)
        assertEquals(1, replacement.created)
    }

    @Test fun leavingTheScreenReleasesItsAdAndLifecycleCollector() {
        show()
        rule.runOnIdle { visible.value = false }
        rule.onNodeWithTag("ad").assertDoesNotExist()
        rule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        assertEquals(1, renderer.created)
        assertEquals(1, renderer.disposed)
        assertNoGap()
    }

    private fun show() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalBannerAdAudience provides audience.value,
                    LocalBannerAdIntegration provides BannerAdIntegration(currentRenderer.value, configuration.value),
                    LocalLifecycleOwner provides owner, LocalTvMode provides television.value) {
                    Column {
                        Text("Before", Modifier.testTag("before"))
                        if (visible.value) BannerAdSlot(AdPlacement.HOME_BANNER)
                        Text("After", Modifier.testTag("after"))
                    }
                }
            }
        }
    }

    private fun assertNoGap() {
        val before = rule.onNodeWithTag("before").fetchSemanticsNode().boundsInRoot
        val after = rule.onNodeWithTag("after").fetchSemanticsNode().boundsInRoot
        assertEquals(before.bottom, after.top, 0.5f)
    }
}

private class FixtureLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

private class CountingRenderer : BannerAdRenderer {
    var created = 0
    var disposed = 0
    var hasFill = true

    @Composable override fun Render(placement: AdPlacement, modifier: Modifier) {
        DisposableEffect(placement) { created++; onDispose { disposed++ } }
        if (hasFill) BannerAdFrame(placement, modifier) { Text("Fixture ad", Modifier.testTag("ad")) }
    }
}
