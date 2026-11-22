package app.zorik.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.zorik.player.testing.RequiresComposeTestManifest
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class SelectionSurfaceContractTest(private val theme: AppTheme, private val fontScale: Float) {
    @get:Rule val rule = createComposeRule()
    private val selected = mutableStateOf(false)
    private var clicks = 0

    @Test fun selectionIsAnnouncedAndDisabledActionsCannotFire() {
        show()
        rule.onNodeWithText("Моє джерело каналів").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("Улюблений канал").performScrollTo().performClick()
        rule.onNodeWithText("Недоступна дія").performScrollTo().assertIsNotEnabled().performClick()
        rule.runOnIdle { assertEquals(2, clicks) }
    }

    @Test fun longLabelsRemainUnclippedAtEverySupportedFontScale() {
        show()
        for (label in listOf("Моє джерело каналів", "Улюблений канал", "Програма передач і опис поточної трансляції")) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Text layout missing", layouts.isEmpty())
            layouts.forEach { layout ->
                // A paragraph can retain maxWidth while Text takes its tighter intrinsic width.
                // Check actual line bounds, not that unused paragraph space (didOverflowWidth).
                assertFalse("$theme/$fontScale clipped height", layout.didOverflowHeight)
                assertFalse("$theme/$fontScale lost lines", layout.multiParagraph.didExceedMaxLines)
                for (line in 0 until layout.lineCount) {
                    assertFalse("Ellipsized $label", layout.isLineEllipsized(line))
                    assertTrue("$theme/$fontScale clipped $label",
                        layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f)
                }
            }
        }
    }

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ZorikTheme(theme) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        UaCard(Modifier.fillMaxWidth(), selected = selected.value,
                            onClick = { selected.value = true; clicks++ }) { Text("Моє джерело каналів") }
                        UaListItem("Улюблений канал", supportingText = "Програма передач і опис поточної трансляції",
                            leadingIcon = AppIcons.Channels, onClick = { clicks++ })
                        UaListItem("Недоступна дія", enabled = false, onClick = { clicks++ })
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> = listOf(AppTheme.AZURE, AppTheme.CINEMA,
            AppTheme.MIDNIGHT, AppTheme.DAYLIGHT).flatMap { theme ->
            listOf(1f, 1.3f, 1.5f, 2f).map { arrayOf(theme, it) }
        }
    }
}
