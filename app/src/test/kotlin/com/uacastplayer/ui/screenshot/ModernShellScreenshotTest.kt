package com.uacastplayer.ui.screenshot

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.EmptyState
import com.uacastplayer.ui.components.GlassTabBar
import com.uacastplayer.ui.components.ScreenHeader
import com.uacastplayer.ui.components.TabBarItem
import com.uacastplayer.ui.favorites.FavoritesScreen
import com.uacastplayer.ui.home.HomeContentState
import com.uacastplayer.ui.home.HomeScreen
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.settings.SettingsOverview
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.LocalReducedEffects
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.theme.appBackground
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real screen components and chrome, synthetic owned content; no network artwork or mock product UI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class ModernShellScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun cinemaDashboardAndTaskPages() {
        setShell(AppTheme.CINEMA, reduced = false)
        capture("modern_home_cinema")
        rule.onNodeWithContentDescription(context.getString(R.string.nav_settings)).performClick()
        capture("modern_settings_cinema")
        rule.onNodeWithContentDescription(context.getString(R.string.nav_favorites)).performClick()
        capture("modern_favorites_cinema")
    }

    @Test fun reducedEffectsStillHaveVisualHierarchy() {
        setShell(AppTheme.DAYLIGHT, reduced = true)
        capture("modern_home_daylight_lite")
    }

    @Test @Config(qualifiers = "uk-w640dp-h280dp-land-xhdpi")
    fun shortWindowKeepsTheEmptyStateActionReachable() {
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            EmptyState(AppIcons.Favorites, "Ще немає обраних каналів", Modifier.size(640.dp, 280.dp),
                "Додавайте канали зірочкою у списку або плеєрі", "Перейти до каналів", {})
        } }
        rule.onNodeWithText("Перейти до каналів").performScrollTo().assertIsDisplayed()
        capture("modern_empty_landscape")
    }

    @Test fun anEmptyStateInsideAScrollingPageDoesNotCreateAnUnboundedNestedScroll() {
        rule.setContent { UaCastTheme(AppTheme.DAYLIGHT) {
            Column(Modifier.size(320.dp, 480.dp).verticalScroll(rememberScrollState())) {
                EmptyState(AppIcons.Favorites, "Немає обраних", primaryActionLabel = "Додати", onPrimaryAction = {})
            }
        } }
        rule.onNodeWithText("Додати").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        // Finish tap feedback before pinning an image; a pressed ripple is not the destination design.
        // 400ms left the Settings tab's ripple mid-fade on some runs, so the favourites reference
        // flipped between two images; a full second is past every press animation in the shell.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun setShell(theme: AppTheme, reduced: Boolean) {
        val channels = List(18) { M3uChannel("Канал ${it + 1}", "https://example.test/${it + 1}") }
        val content = HomeContentState(PlaylistUiState(
            groups = listOf(GroupedChannels(ChannelGroup.Custom("Кіно"), channels.take(9)),
                GroupedChannels(ChannelGroup.Custom("Новини"), channels.drop(9))),
            displayName = "Моя телебібліотека", activePlaylistId = "demo"), EpgUiState(nowMillis = 0),
            IconPrefetchUiState(), listOf(FavoriteChannel("demo", "Кіно HD", channels[0].streamUrl,
                null, "Кіно", 42)), null)
        val source = HomeSourceState(emptyList(), null, {}, {}, {}, {})
        rule.setContent { UaCastTheme(theme) {
            CompositionLocalProvider(LocalReducedEffects provides reduced) {
                var page by remember { mutableIntStateOf(0) }
                val names = listOf(R.string.nav_home, R.string.nav_channels, R.string.nav_favorites,
                    R.string.nav_settings).map { stringResource(it) }
                val icons = listOf(AppIcons.Home, AppIcons.Channels, AppIcons.Favorites, AppIcons.Settings)
                Scaffold(Modifier.size(411.dp, 891.dp).appBackground(),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    topBar = { ScreenHeader(if (page == 0) "Zorik" else names[page], labelRemote = page == 0) },
                    bottomBar = { GlassTabBar(names.mapIndexed { index, name ->
                        TabBarItem(name, icons[index], page == index, { page = index })
                    }) }) { padding ->
                    when (page) {
                        2 -> FavoritesScreen(emptyList(), emptyList(), FavoritesSortOrder.MANUAL,
                            {}, { _, _ -> }, {}, {}, {}, { null }, Modifier.padding(padding))
                        3 -> Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsOverview({}, {}, {}, {}, {})
                        }
                        else -> HomeScreen(content, source, { null }, { _, _ -> }, {}, Modifier.padding(padding))
                    }
                }
            }
        } }
    }
}
