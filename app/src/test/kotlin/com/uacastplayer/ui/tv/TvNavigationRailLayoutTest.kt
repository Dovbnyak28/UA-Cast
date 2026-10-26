package com.uacastplayer.ui.tv

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.GlassNavigationRail
import com.uacastplayer.ui.components.TabBarItem
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
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
            UaCastTheme(AppTheme.CINEMA) {
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
