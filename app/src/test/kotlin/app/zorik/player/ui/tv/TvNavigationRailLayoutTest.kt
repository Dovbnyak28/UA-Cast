package app.zorik.player.ui.tv

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.components.GlassNavigationRail
import app.zorik.player.ui.components.TabBarItem
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reproduces the MiTV 720p rail at the actual TV minimum font scale. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w960dp-h540dp-television-tvdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class TvNavigationRailLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fullUkrainianDestinationsFitWithoutMidWordWrappingOrTruncation() {
        val labels = listOf("Головна", "Канали", "Улюблені", "Налаштування")
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    GlassNavigationRail(labels.map { label ->
                        TabBarItem(if (label == "Налаштування") "Налашт." else label,
                            AppIcons.Home, selected = false, onClick = {}, contentDescription = label)
                    })
                }
            }
        }
        labels.forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertEquals("$label must occupy one line", 1, layouts.single().lineCount)
            val layout = layouts.single()
            val diagnostic = "$label exceeds the rail's text width: size=${layout.size}, " +
                "paragraph=${layout.multiParagraph.width}, line=${layout.getLineRight(0)}"
            assertFalse(diagnostic,
                layout.didOverflowWidth)
            assertFalse("$label must not be ellipsized", layout.isLineEllipsized(0))
        }
        rule.onNodeWithText("Налашт.").assertDoesNotExist()
    }
}
