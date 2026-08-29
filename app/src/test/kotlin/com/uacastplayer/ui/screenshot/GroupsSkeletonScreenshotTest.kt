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
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.channels.GroupsSkeletonGrid
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
 * The loading skeleton is the one screen in the app that is *unreachable* on a device without
 * destroying the user's data: it only shows when no playlist has ever loaded, and this app restores
 * one from cache on launch. A golden is therefore the only honest way to check that it holds the
 * right shape - by hand it is either never seen or seen for the half second before the cache wins.
 *
 * The shimmer sweep is an infinite animation; Robolectric renders at a fixed clock, so what the
 * golden pins is the layout and the base colours, not the highlight's position.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class GroupsSkeletonScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupsSkeleton_grid() {
        capture(ChannelLayout.GRID, "groups_skeleton_grid")
    }

    @Test
    fun groupsSkeleton_list() {
        capture(ChannelLayout.LIST, "groups_skeleton_list")
    }

    private fun capture(layout: ChannelLayout, name: String) {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 700.dp)
                        .background(UaTheme.palette.void)
                        .padding(horizontal = ScreenHPadding),
                ) {
                    GroupsSkeletonGrid(layout = layout)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
