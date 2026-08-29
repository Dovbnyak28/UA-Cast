package com.uacastplayer.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.GlassNavigationRail
import com.uacastplayer.ui.components.TabBarItem
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/** The shortest expanded viewport still has to show all four rail destinations and labels. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "uk-w600dp-h360dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class NavigationRailLayoutTest(private val fontScale: Float) {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun destinationsStayInsideShortLandscapeRailAtEveryFontScale() {
        composeRule.setContent {
            ScaledRail(fontScale)
        }

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        listOf("Головна", "Канали", "Улюблені", "Налаштування").forEach { description ->
            val icon = composeRule.onNodeWithContentDescription(description)
            icon.assertIsDisplayed()
            val bounds = icon.getUnclippedBoundsInRoot()
            assertTrue(
                "rail destination $description is clipped at fontScale=$fontScale: $bounds vs $root",
                bounds.left >= root.left && bounds.top >= root.top &&
                    bounds.right <= root.right && bounds.bottom <= root.bottom,
            )
        }
        if (fontScale >= 1.5f) {
            composeRule.onNodeWithText("Налашт.").assertDoesNotExist()
        } else {
            val label = composeRule.onNodeWithText("Налашт.")
            label.assertIsDisplayed()
            val bounds = label.getUnclippedBoundsInRoot()
            assertTrue(
                "rail label is clipped at fontScale=$fontScale: $bounds vs $root",
                bounds.left >= root.left && bounds.top >= root.top &&
                    bounds.right <= root.right && bounds.bottom <= root.bottom,
            )
        }
    }

    @Composable
    private fun ScaledRail(scale: Float) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
            UaCastTheme(AppTheme.CINEMA) {
                GlassNavigationRail(
                    items = listOf(
                        TabBarItem("Головна", AppIcons.Home, selected = true, onClick = {}),
                        TabBarItem("Канали", AppIcons.Channels, selected = false, onClick = {}),
                        TabBarItem("Улюблені", AppIcons.Favorites, selected = false, onClick = {}),
                        TabBarItem(
                            label = "Налашт.",
                            icon = AppIcons.Settings,
                            selected = false,
                            onClick = {},
                            contentDescription = "Налаштування",
                        ),
                    ),
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "fontScale={0}")
        fun fontScales(): List<Array<Any>> =
            listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 2.0f).map { arrayOf<Any>(it) }
    }
}
