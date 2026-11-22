package app.zorik.player.ui.player

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.R
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.UiTestTags
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class PlayerChannelSheetUsabilityTest {
    @get:Rule val rule = createComposeRule()
    @After fun restoreFontScale() { RuntimeEnvironment.setFontScale(1f) }
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val original = listOf("News", "España HD", "Film").mapIndexed { i, name ->
        M3uChannel(name, "https://example.test/$i")
    }
    private val channels = mutableStateOf(original)
    private val selections = mutableListOf<Int>()
    private var dismissals = 0

    @Test fun imeSearchFinishesEditingAndKeepsTheResultSelectable() {
        show()
        assertEquals(ImeAction.Search, search().fetchSemanticsNode().config[SemanticsProperties.ImeAction])
        search().performTextInput("España")
        search().performImeAction()
        search().assertIsNotFocused()
        awaitName("España HD")
        rule.onNodeWithText("España HD").performClick()
        assertEquals(listOf(1), selections)
        assertEquals(1, dismissals)
    }

    @Test fun noMatchesCanBeClearedWithoutRetyping() {
        show()
        search().performTextInput("missing")
        awaitName(context.getString(R.string.player_channels_no_results))
        rule.onNodeWithContentDescription(context.getString(R.string.channels_clear_search)).performClick()
        assertEquals("", search().fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        awaitName("Film")
        rule.onNodeWithText("Film").assertIsSelected()
    }

    @Test fun filteredSelectionKeepsTheOriginalSessionIndexAndFiresOnce() {
        show()
        search().performTextInput("España")
        awaitName("España HD")
        rule.onNodeWithText("España HD").performClick()
        assertEquals(listOf(1), selections)
        assertEquals(1, dismissals)
    }

    @Test fun replacingTheSessionDuringSearchNeverUsesTheOldRowsOrIndices() {
        show()
        search().performTextInput("España")
        rule.runOnIdle { channels.value = listOf(M3uChannel("España nueva", "https://example.test/new")) }
        awaitName("España nueva")
        rule.onNodeWithText("España HD").assertDoesNotExist()
        rule.onNodeWithText("España nueva").performClick()
        assertEquals(listOf(0), selections)
        assertEquals(1, dismissals)
    }

    @Test fun queryAndFilteredSessionIndexSurviveStateRestoration() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { ZorikTheme(AppTheme.CINEMA) {
            PlayerChannelSheet(channels.value, original[2], { selections += it }, { dismissals++ })
        } }
        search().performTextInput("España")
        awaitName("España HD")
        restoration.emulateSavedInstanceStateRestore()
        assertEquals("España", search().fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        awaitName("España HD")
        rule.onNodeWithText("España HD").performClick()
        assertEquals(listOf(1), selections)
    }

    @Test @Config(qualifiers = "uk-w320dp-h640dp-xhdpi")
    fun longSelectedNameRemainsReadableAtActualTwoHundredPercent() {
        RuntimeEnvironment.setFontScale(2f)
        val name = "Новини регіону — повний вечірній випуск недільної програми"
        channels.value = listOf(original[2].copy(displayName = name))
        show(current = channels.value[0])
        awaitName(name)
        rule.onNodeWithText(name).assertIsSelected().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2f, layouts.single().layoutInput.density.fontScale, 0.01f)
        assertFalse(layouts.single().hasVisualOverflow)
        rule.onRoot().captureRoboImage("src/test/screenshots/channel_picker_large_text_uk.png")
    }

    @Test @Config(qualifiers = "uk-w640dp-h240dp-land-xhdpi")
    fun currentChannelIsReachableInShortLandscapeWithLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        show()
        awaitName("Film")
        rule.onNodeWithTag(UiTestTags.PLAYER_CHANNEL_LIST).performScrollToNode(hasText("Film"))
        val row = rule.onNodeWithText("Film").assertIsSelected().assertIsDisplayed()
        val viewport = rule.onNodeWithTag(UiTestTags.PLAYER_CHANNEL_LIST).getBoundsInRoot()
        val bounds = row.getUnclippedBoundsInRoot()
        assertTrue("Current row clipped by $viewport: $bounds",
            bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        row.performClick()
        assertEquals(listOf(2), selections)
        rule.onRoot().captureRoboImage("src/test/screenshots/channel_picker_short_uk.png")
    }

    private fun show(current: M3uChannel = original[2], source: MutableState<List<M3uChannel>> = channels) {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            PlayerChannelSheet(source.value, current, { selections += it }, { dismissals++ })
        } }
    }

    private fun search() = rule.onNode(hasSetTextAction())
    private fun awaitName(name: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
    }
}
