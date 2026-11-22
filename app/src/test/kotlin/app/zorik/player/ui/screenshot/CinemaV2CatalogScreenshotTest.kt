package app.zorik.player.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.zorik.player.epg.EpgData
import app.zorik.player.epg.EpgIndex
import app.zorik.player.epg.EpgChannel
import app.zorik.player.epg.EpgProgramme
import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.favorites.FavoriteKey
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.epg.EpgUiState
import app.zorik.player.icons.IconPrefetchUiState
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.channels.SingleGroupChannelList
import app.zorik.player.ui.home.HomeContentState
import app.zorik.player.ui.home.HomeScreen
import app.zorik.player.ui.home.HomeSourceState
import app.zorik.player.ui.media.LocalOpenVideo
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import app.zorik.player.ui.theme.appBackground
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaV2CatalogScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val channels = (1..20).map {
        M3uChannel("Канал $it — Кіно та документальні фільми", "https://example.test/$it", tvgId = "$it")
    }
    private val group = GroupedChannels(ChannelGroup.Custom("Кіно й телесеріали"), channels)
    private val now = 1_700_000_000_000L
    private val epg = EpgUiState(nowMillis = now, data = EpgData(
        EpgIndex(channels.map { EpgChannel(it.tvgId!!, listOf(it.displayName), null) }),
        channels.associate { channel ->
            val id = checkNotNull(channel.tvgId)
            id to listOf(EpgProgramme(id,
            now - 1_200_000, now + 2_400_000, "Подорож у світ кіно")) }))

    @Test fun home320Cinema() = home(AppTheme.CINEMA, "cinema_v2_home_320")
    @Test fun home320Daylight() = home(AppTheme.DAYLIGHT, "cinema_v2_home_daylight_320")
    @Test fun home320Azure() = home(AppTheme.AZURE, "cinema_v2_home_azure_320")
    @Test fun home320Midnight() = home(AppTheme.MIDNIGHT, "cinema_v2_home_midnight_320")
    @Test fun home320System() = home(AppTheme.SYSTEM, "cinema_v2_home_system_320")
    @Test fun home320LargeFont() = home(AppTheme.CINEMA, "cinema_v2_home_font200_320", 2f)
    private fun home(theme: AppTheme, name: String, scale: Float = 1f) {
        rule.setContent { ZorikTheme(theme) { CompositionLocalProvider(LocalOpenVideo provides {},
            LocalDensity provides Density(LocalDensity.current.density, scale)) {
            Box(Modifier.fillMaxSize().appBackground()) {
                HomeScreen(HomeContentState(PlaylistUiState(groups = listOf(group),
                    displayName = "Мій плейлист"),
                    epg, IconPrefetchUiState(), channels.take(3).map { FavoriteChannel(
                        FavoriteKey.of(it), it.displayName, it.streamUrl, it.tvgId, it.groupTitle) },
                    FavoriteKey.of(channels.first())),
                    HomeSourceState(emptyList(), null, {}, {}, {}, {}), { null }, { _, _ -> }, {})
            }
        } } }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Продовжити перегляд").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun list320Cinema() = catalog(ChannelLayout.LIST, "cinema_v2_channels_list_320")
    @Test fun grid320Cinema() = catalog(ChannelLayout.GRID, "cinema_v2_channels_grid_320")
    @Test fun list320LargeFont() = catalog(ChannelLayout.LIST, "cinema_v2_channels_font200_320", 2f)
    private fun catalog(layout: ChannelLayout, name: String, scale: Float = 1f) {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) { CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, scale)) {
            Box(Modifier.fillMaxSize().appBackground()) {
                SingleGroupChannelList(group, epg, 0, { null }, ListDensity.FULL,
                    layout, {}, { false }, {}, { false }, {}, {}, {}, onGuide = {})
            }
        } } }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
