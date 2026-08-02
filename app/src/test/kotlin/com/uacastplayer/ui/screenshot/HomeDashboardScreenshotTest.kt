package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.home.HomeContentState
import com.uacastplayer.ui.home.HomeScreen
import com.uacastplayer.ui.home.HomeSourceState
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

/**
 * Golden coverage for the home dashboard *with data in it*.
 *
 * The only goldens this project had covered empty states, which is how "1 Улюблених" and
 * "2863 Каналів" shipped on the app's main screen in its primary language: nothing that ran in CI
 * had ever rendered the screen with a number on it. The counts here are picked for that - 3
 * channels, 1 group, 1 favorite - so the labels land on the "few" and singular forms, the two a
 * fixed genitive-plural label gets wrong. A regression to a fixed label moves these pixels.
 *
 * Ukrainian and English both, because they fail differently: Ukrainian needs three plural classes
 * and reads as broken grammar, English needs one and reads as "1 Favorites".
 *
 * See [DesignSystemScreenshotTest] for how to record and verify.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class HomeDashboardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun channel(name: String) = M3uChannel(displayName = name, streamUrl = "https://example/$name.m3u8")

    private val content = HomeContentState(
        playlistState = PlaylistUiState(
            groups = listOf(
                GroupedChannels(
                    group = ChannelGroup.Custom("Movies"),
                    channels = listOf(channel("First"), channel("Second"), channel("Third")),
                ),
            ),
            activePlaylistId = "6368ffd4",
            restoredFromCache = true,
        ),
        // Pinned rather than defaulted: EpgUiState.nowMillis defaults to the wall clock, which
        // would make this golden depend on when it was recorded.
        epgState = EpgUiState(nowMillis = 0L),
        iconPrefetchState = IconPrefetchUiState(),
        favorites = listOf(
            FavoriteChannel(
                key = "fav",
                displayName = "Favorite One",
                streamUrl = "https://example/fav.m3u8",
                tvgId = null,
                groupTitle = null,
            ),
        ),
        lastWatchedChannelKey = null,
    )

    private val source = HomeSourceState(
        playlistSources = emptyList(),
        activePlaylistSourceId = null,
        onSwitchPlaylistSource = {},
        onRemovePlaylistSource = {},
        onOpenAddPlaylist = {},
        onRefreshPlaylist = {},
    )

    private fun capture(name: String, theme: AppTheme = AppTheme.CINEMA) {
        composeRule.setContent {
            UaCastTheme(theme) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 891.dp)
                        .background(UaTheme.palette.void),
                ) {
                    HomeScreen(
                        content = content,
                        source = source,
                        resolveIcon = { null },
                        onChannelSelected = { _, _ -> },
                        onOpenChannels = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun homeDashboard_ukrainian() = capture("home_dashboard_uk")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun homeDashboard_english() = capture("home_dashboard_en")

    /** Midnight on a populated screen, not just the empty state: it is the only theme that turns
     * the background texture off entirely (see UaPalette.wallpaperTexture), so a full dashboard is
     * where a regression in that would actually show. */
    @Test
    @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun homeDashboard_midnight() = capture("home_dashboard_midnight", AppTheme.MIDNIGHT)
}
