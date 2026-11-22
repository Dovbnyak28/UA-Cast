package app.zorik.player.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.components.GlassNavigationRail
import app.zorik.player.ui.components.TabBarItem
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
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
            icon.performScrollTo()
            icon.assertIsDisplayed()
            val bounds = icon.getUnclippedBoundsInRoot()
            assertTrue(
                "rail destination $description is clipped at fontScale=$fontScale: $bounds vs $root",
                bounds.left >= root.left && bounds.top >= root.top &&
                    bounds.right <= root.right && bounds.bottom <= root.bottom,
            )
        }
        run {
            val label = composeRule.onNodeWithText("Налашт.")
            label.performScrollTo()
            label.assertIsDisplayed()
            val bounds = label.getUnclippedBoundsInRoot()
            assertTrue(
                "rail label is clipped at fontScale=$fontScale: $bounds vs $root",
                bounds.left >= root.left && bounds.top >= root.top &&
                    bounds.right <= root.right && bounds.bottom <= root.bottom,
            )
        }
    }

    @Test
    fun shortRailKeepsEveryDestinationVisibleWithoutScrolling() {
        composeRule.setContent {
            Box(Modifier.height(205.dp)) { ScaledRail(fontScale) }
        }
        listOf("Головна", "Канали", "Улюблені", "Налаштування").forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        val actions = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(4, actions.size)
        actions.forEach { action ->
            assertTrue("Navigation target is shorter than 48dp", action.boundsInRoot.height >=
                48f * composeRule.density.density - 1f)
        }
    }

    @Test
    fun mediumShortRailKeepsDestinationsVisibleWithoutScrolling() {
        composeRule.setContent {
            Box(Modifier.height(300.dp)) { ScaledRail(fontScale) }
        }
        listOf("Головна", "Канали", "Улюблені", "Налаштування").forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
    }

    @Composable
    private fun ScaledRail(scale: Float) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
            ZorikTheme(AppTheme.CINEMA) {
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
