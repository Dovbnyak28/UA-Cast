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
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.home.HomeDashboardSkeleton
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
 * Home's half of the loading state, and unreachable by hand for the same reason
 * [GroupsSkeletonScreenshotTest] documents - plus one of its own: Home is the launch tab, so the
 * only way to see this on a device is to be quick enough during a cold start, which on a warm page
 * cache is over in a couple of seconds.
 *
 * What makes it worth a golden rather than a unit assertion is what it replaced. This branch did
 * not exist: Home tested only `hasChannels`, so a restore in progress rendered the "no playlist yet"
 * empty state and an add-a-playlist button over a cached playlist of 2863 channels. A golden is what
 * keeps the middle branch from being dropped again in a refactor - the empty state would come back
 * and look, to anyone not doing a cold start, entirely correct.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class HomeSkeletonScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeDashboardSkeleton() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 700.dp)
                        .background(UaTheme.palette.void)
                        .padding(horizontal = ScreenHPadding),
                ) {
                    HomeDashboardSkeleton()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/home_skeleton.png")
    }
}
