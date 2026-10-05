package com.uacastplayer.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.EmptyState
import com.uacastplayer.ui.components.GlassTabBar
import com.uacastplayer.ui.components.TabBarItem
import com.uacastplayer.ui.nav.largeTextLabelRes
import com.uacastplayer.ui.nav.tabLabelRes
import com.uacastplayer.ui.theme.AppIcons
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
 * Golden-image coverage for the design system. Complements the `@Preview` functions (see
 * docs/DESIGN_SYSTEM.md), which catch the same class of regression at authoring time but only for
 * whoever happens to open the preview - these fail the build instead.
 *
 * Goldens live in `app/src/test/screenshots` and are regenerated with:
 *
 *     ./gradlew :app:recordRoborazziDebug
 *
 * and verified (the CI-facing mode, which fails on a pixel diff) with:
 *
 *     ./gradlew :app:verifyRoborazziDebug
 *
 * `qualifiers` pins the rendering surface so a golden recorded on one machine matches another:
 * without it Robolectric picks a default screen size and density, and any change to either
 * rewrites every image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class DesignSystemScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_cinema() {
        captureThemed(AppTheme.CINEMA, "empty_state_cinema")
    }

    @Test
    fun emptyState_azure() {
        captureThemed(AppTheme.AZURE, "empty_state_azure")
    }

    @Test
    fun emptyState_midnight() {
        captureThemed(AppTheme.MIDNIGHT, "empty_state_midnight")
    }

    @Test
    @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun selectedNavigation_azure() = captureNavigation(AppTheme.AZURE, "navigation_azure")

    @Test
    @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun selectedNavigation_cinema() = captureNavigation(AppTheme.CINEMA, "navigation_cinema")

    @Test
    @Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
    fun selectedNavigation_midnight() = captureNavigation(AppTheme.MIDNIGHT, "navigation_midnight")

    private fun captureNavigation(theme: AppTheme, name: String) {
        composeRule.setContent {
            UaCastTheme(theme) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 120.dp)
                        .background(UaTheme.palette.void),
                ) {
                    GlassTabBar(
                        items = BottomDestination.entries.map { destination ->
                            val label = stringResource(destination.tabLabelRes())
                            TabBarItem(
                                label = label,
                                largeTextLabel = stringResource(destination.largeTextLabelRes()),
                                icon = when (destination) {
                                    BottomDestination.HOME -> AppIcons.Home
                                    BottomDestination.CHANNELS -> AppIcons.Channels
                                    BottomDestination.FAVORITES -> AppIcons.Favorites
                                    BottomDestination.SETTINGS -> AppIcons.Settings
                                },
                                selected = destination == BottomDestination.HOME,
                                onClick = {},
                            )
                        },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun captureThemed(theme: AppTheme, name: String) {
        composeRule.setContent {
            UaCastTheme(theme) {
                Box(
                    Modifier
                        .size(width = 411.dp, height = 400.dp)
                        .background(UaTheme.palette.void)
                ) {
                    EmptyState(
                        icon = AppIcons.Lock,
                        title = "Немає каналів",
                        subtitle = "Додайте плейлист, щоб почати",
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
