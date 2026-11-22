package app.zorik.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class ComponentFoundationContractTest(private val theme: AppTheme, private val fontScale: Float) {
    @get:Rule val rule = createComposeRule()

    @Test fun actionsExposeButtonRolesAndRealNonOverlappingFortyEightDpBounds() {
        show()
        for (label in listOf("Відкрити відеофайл на пристрої", "Завантажити резервну копію")) {
            val node = rule.onNodeWithText(label).performScrollTo()
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$theme/$fontScale action too short: $bounds", bounds.bottom - bounds.top >= 48.dp)
        }
        val first = rule.onNodeWithContentDescription("Попередній").performScrollTo().getUnclippedBoundsInRoot()
        val second = rule.onNodeWithContentDescription("Налаштування").getUnclippedBoundsInRoot()
        for (bounds in listOf(first, second)) {
            assertTrue("Icon action must own its actual target",
                bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp)
        }
        assertTrue("Icon targets overlap", first.right <= second.left)
    }

    @Test fun singleChoiceOptionsAreReachableSelectedAndAtLeastFortyEightDpTall() {
        show()
        val bounds = listOf("Канали", "Обране", "Телегід").map { label ->
            val node = rule.onNodeWithText(label).performScrollTo()
            node.performClick().assertIsSelected()
            node.getUnclippedBoundsInRoot().also {
                assertTrue("$theme/$fontScale short segment: $it", it.bottom - it.top >= 48.dp)
            }
        }
        bounds.zipWithNext().forEach { (first, second) ->
            assertTrue("Segments overlap", first.right <= second.left || first.bottom <= second.top)
        }
    }

    @Test fun segmentLabelsDoNotBreakIntoIndividualLettersOnNarrowScreens() {
        show()
        for (label in listOf("Канали", "Обране", "Телегід")) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { assertEquals("$theme/$fontScale split $label", 1, it.lineCount) }
        }
    }

    private fun show() {
        val selected = mutableIntStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ZorikTheme(theme) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton("Відкрити відеофайл на пристрої", {}, Modifier.fillMaxWidth())
                        SecondaryButton("Завантажити резервну копію", {}, Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RoundIconButton(AppIcons.SkipPrevious, {}, contentDescription = "Попередній")
                            SmallRoundIconButton(AppIcons.Settings, {}, contentDescription = "Налаштування")
                        }
                        SegmentedControl(listOf("Канали", "Обране", "Телегід"), selected.intValue,
                            { selected.intValue = it })
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> = listOf(AppTheme.AZURE, AppTheme.CINEMA,
            AppTheme.MIDNIGHT, AppTheme.DAYLIGHT).flatMap { theme ->
            listOf(1f, 1.3f, 1.5f, 2f).map { scale -> arrayOf(theme, scale) }
        }
    }
}
