package com.uacastplayer.ui.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.R
import com.uacastplayer.ads.AdPlacement
import com.uacastplayer.ads.AdRequestPermission
import com.uacastplayer.ads.BannerAdAudience
import com.uacastplayer.ads.BannerAdConfiguration
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.License
import com.uacastplayer.premium.LicenseTier
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.channels.ChannelsScreen
import com.uacastplayer.ui.home.HomeContentState
import com.uacastplayer.ui.home.HomeScreen
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.appBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class BannerAdScreenTest {
    @get:Rule val rule = createComposeRule()
    private val channels = listOf(
        M3uChannel("First", "https://unused.example.test/first"),
        M3uChannel("Second", "https://unused.example.test/second"),
    )
    private val playlist = mutableStateOf(PlaylistUiState(
        groups = listOf(GroupedChannels(ChannelGroup.Ungrouped, channels)), displayName = "Fixture playlist"))
    private val home = mutableStateOf(false)
    private val audience = mutableStateOf(BannerAdAudience(Entitlements.FREE))
    private val renderer = ScreenFixtureAdRenderer()
    private val lifecycleOwner = ScreenFixtureLifecycleOwner()
    private var selectedIndex: Int? = null
    private var selectedChannels: List<M3uChannel>? = null
    private var selections = 0
    private var openChannels = 0

    @Test fun localPlaylistBannerIsVisibleAndDoesNotBreakChannelSelection() {
        show()
        assertChannelsBannerInsideViewport()
        rule.onRoot().captureRoboImage("src/test/screenshots/lite_channels_ad_fixture.png")
        selectSecondChannel()
    }

    @Test fun urlPlaylistRefreshContainerReservesBannerSpaceAndKeepsSelection() {
        playlist.value = playlist.value.copy(sourceUrl = "https://unused.example.test/playlist.m3u")
        show()
        assertChannelsBannerInsideViewport()
        selectSecondChannel()
    }

    @Test fun purchaseRemovesTheActualChannelsBannerAndReleasesItsRequest() {
        show()
        assertChannelsBannerInsideViewport()
        rule.runOnIdle { audience.value = BannerAdAudience(Entitlements.of(License(LicenseTier.LIFETIME), 0L)) }
        rule.onNodeWithTag("ad_slot_CHANNELS_BANNER").assertDoesNotExist()
        assertEquals(1, renderer.disposed)
        selectSecondChannel()
    }

    @Test fun emptyOrFirstLoadingScreensDoNotInvokeTheProvider() {
        playlist.value = PlaylistUiState()
        show()
        rule.runOnIdle { home.value = true }
        rule.runOnIdle { playlist.value = PlaylistUiState(isLoading = true) }
        rule.runOnIdle { home.value = false }
        rule.onNodeWithTag("fixture_ad").assertDoesNotExist()
        assertTrue(renderer.mounted.isEmpty())
    }

    @Test fun homeBannerComesAfterPrimaryContentAndDoesNotInterceptItsAction() {
        home.value = true
        show()
        rule.onNodeWithTag("ad_slot_HOME_BANNER").performScrollTo().assertIsDisplayed()
        val banner = rule.onNodeWithTag("ad_slot_HOME_BANNER").fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithText(context.getString(R.string.home_view_channels_button))
        assertTrue(button.fetchSemanticsNode().boundsInRoot.bottom <= banner.top)
        rule.onRoot().captureRoboImage("src/test/screenshots/lite_home_ad_fixture.png")
        button.performScrollTo().performClick()
        assertEquals(1, openChannels)
        assertEquals(listOf(AdPlacement.HOME_BANNER), renderer.mounted)
    }

    @Test fun premiumSuppressesBothActualScreenPlacements() {
        audience.value = BannerAdAudience(Entitlements.of(License(LicenseTier.LIFETIME), 0L))
        show()
        rule.runOnIdle { home.value = true }
        rule.onNodeWithTag("fixture_ad").assertDoesNotExist()
        assertTrue(renderer.mounted.isEmpty())
    }

    @Test fun noFillNeverAddsAnEmptyFooterToTheActualScreens() {
        renderer.hasFill = false
        show()
        rule.onNodeWithTag("ad_slot_CHANNELS_BANNER").assertDoesNotExist()
        rule.runOnIdle { home.value = true }
        rule.onNodeWithTag("ad_slot_HOME_BANNER").assertDoesNotExist()
        assertEquals(listOf(AdPlacement.CHANNELS_BANNER, AdPlacement.HOME_BANNER), renderer.mounted)
        assertEquals(1, renderer.disposed)
    }

    @Test @Config(qualifiers = "uk-w960dp-h891dp-xhdpi")
    fun loadedBannerIsCenteredAndCappedEvenWhenTheScreenRequestsFullWidth() {
        show()
        val banner = rule.onNodeWithTag("ad_slot_CHANNELS_BANNER").fetchSemanticsNode().boundsInRoot
        val screen = rule.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        assertEquals(with(rule.density) { 640.dp.toPx() }, banner.width, 1f)
        assertEquals(screen.center.x, banner.center.x, 1f)
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner,
                    LocalBannerAdAudience provides audience.value,
                    LocalBannerAdIntegration provides BannerAdIntegration(renderer,
                        BannerAdConfiguration(AdPlacement.entries.toSet(), AdRequestPermission.ALLOWED))) {
                    Box(Modifier.fillMaxSize().appBackground().testTag("screen")) {
                        if (home.value) HomeScreen(
                            HomeContentState(playlist.value, EpgUiState(), IconPrefetchUiState(), emptyList(), null),
                            HomeSourceState(emptyList(), null, {}, {}, {}, {}), { null }, { _, _ -> },
                            { openChannels++ },
                        ) else ChannelsScreen(playlist.value,
                            { items, index -> selectedChannels = items; selectedIndex = index; selections++ },
                            EpgUiState(), IconPrefetchUiState(), { null }, ListDensity.MINIMAL,
                            ChannelLayout.LIST, {}, { false }, {}, { false }, {}, {}, {}, false, {}, {},
                            emptySet(), emptySet(), {}, {}, {}, {},
                        )
                    }
                }
            }
        }
    }

    private fun assertChannelsBannerInsideViewport() {
        rule.onNodeWithTag("ad_slot_CHANNELS_BANNER").assertIsDisplayed()
        val banner = rule.onNodeWithTag("ad_slot_CHANNELS_BANNER").fetchSemanticsNode().boundsInRoot
        val screen = rule.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        assertTrue(banner.top > screen.top && banner.bottom <= screen.bottom)
        val search = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(search.bottom <= banner.top)
        assertEquals(listOf(AdPlacement.CHANNELS_BANNER), renderer.mounted)
    }

    private fun selectSecondChannel() {
        // A different query cannot be mistaken for the row's text while async search is still pending.
        rule.onNode(hasSetTextAction()).performTextInput("sec")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Second").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Second").performClick()
        rule.runOnIdle {
            assertEquals(1, selectedIndex)
            assertEquals(channels, selectedChannels)
            assertEquals(1, selections)
        }
    }
}

private class ScreenFixtureLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

/** Test-only loaded banner, not an installed advertising provider or a production placeholder. */
private class ScreenFixtureAdRenderer : BannerAdRenderer {
    val mounted = mutableListOf<AdPlacement>()
    var disposed = 0
    var hasFill = true

    @Composable override fun Render(placement: AdPlacement, modifier: Modifier) {
        DisposableEffect(placement) { mounted.add(placement); onDispose { disposed++ } }
        if (hasFill) BannerAdFrame(placement, modifier) {
            Box(Modifier.fillMaxWidth().height(50.dp).background(UaTheme.palette.surface1).testTag("fixture_ad"),
                contentAlignment = Alignment.Center) {
                Text("Тестовий банер · не справжня реклама", style = Caption, color = UaTheme.palette.labelSecondary)
            }
        }
    }
}
