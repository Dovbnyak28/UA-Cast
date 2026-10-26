package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.channels.GroupsOverviewGrid
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.ScreenHPadding
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
 * The first screen a user with a playlist looks at, and until now the only one of its size with no
 * test of any kind behind it.
 *
 * That gap is what this exists for rather than any single assertion. Two illustration schemes have
 * been taken off these cards - a collage of cached channel logos, then a set of curated
 * per-category pictures - and both changes were verified by looking at a phone. A golden is what
 * makes the third change tell someone, instead of the user finding out.
 *
 * What it pins in particular is the badge rule, which is easy to break silently and impossible to
 * see in a diff: **every card carries a plate, and only a group whose name states a quality puts
 * text in it.** The fixture below is built so a regression in either half shows up - `4K` is a
 * quality group, `Фільми`/`Спорт` are categories that used to have illustrations and must now show
 * the engraved mark, and `Sport FHD` is the case the old code got wrong, matching its category
 * before its label and so never showing `HD` at all.
 *
 * Both themes are captured. The mark is drawn from [UaTheme] tokens - `void` for the groove's
 * shadow, `edgeHighlightStrong` for its lower lip - and `void` is a near-black in Cinema but a true
 * black in Midnight, so the two goldens are not the same picture with different hues.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class GroupsOverviewScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun channels(count: Int, prefix: String): List<M3uChannel> =
        (1..count).map { M3uChannel(displayName = "$prefix $it", streamUrl = "http://example.test/$prefix/$it") }

    /** Ordered so the first row holds one of each badge outcome - see the class doc. */
    private fun sampleGroups(): List<GroupedChannels> = listOf(
        GroupedChannels(ChannelGroup.Known(ChannelGroup.KEY_MOVIES), channels(516, "Movie")),
        GroupedChannels(ChannelGroup.Custom("4K"), channels(54, "UHD")),
        GroupedChannels(ChannelGroup.Known(ChannelGroup.KEY_SPORTS), channels(443, "Sport")),
        GroupedChannels(ChannelGroup.Custom("Sport FHD"), channels(31, "SportHd")),
        GroupedChannels(ChannelGroup.Ungrouped, channels(12, "Other")),
    )

    @Test
    fun groupsOverview_cinema() {
        capture(AppTheme.CINEMA, "groups_overview_cinema")
    }

    @Test
    fun groupsOverview_midnight() {
        capture(AppTheme.MIDNIGHT, "groups_overview_midnight")
    }

    private fun capture(theme: AppTheme, name: String) {
        composeRule.setContent {
            UaCastTheme(theme) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 700.dp)
                        .background(UaTheme.palette.void)
                        .padding(horizontal = ScreenHPadding),
                ) {
                    GroupsOverviewGrid(
                        groups = sampleGroups(),
                        gridState = rememberLazyGridState(),
                        layout = ChannelLayout.GRID,
                        onLayoutChange = {},
                        onGroupClick = {},
                        iconRefreshKey = 0,
                        // No icon resolution on this screen since the card art was removed; a
                        // fixture that returned a file would be describing a path that no longer
                        // exists.
                        resolveIcon = { null },
                        isFavorite = { false },
                        onToggleFavorite = {},
                        onChannelClick = {},
                        pinnedGroupKeys = emptySet(),
                        hiddenGroupKeys = emptySet(),
                        onPinGroup = {},
                        onHideGroup = {},
                        onClearGroupOverride = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
