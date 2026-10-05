package com.uacastplayer.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.tv.TvInputRegistry
import com.uacastplayer.ui.tv.TvPresentation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlaylistSourceSafetyTest {
    @get:Rule val rule = createComposeRule()

    private val selectableSources = listOf(
        PlaylistSource("first", PlaylistSourceType.URL, "https://example/first", "First source", 1),
        PlaylistSource("second", PlaylistSourceType.URL, "https://example/second", "Second source", 2),
    )

    @Test fun `active and inactive source rows expose their selection to accessibility`() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceSheet(selectableSources, "first", {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("First source").assertIsSelected()
        rule.onNodeWithText("Second source").assertIsNotSelected()
    }

    @Test fun `switching a source updates selection without duplicate callbacks`() {
        val activeId = mutableStateOf("first")
        val selections = mutableListOf<String>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceSheet(selectableSources, activeId.value, {
                    selections += it.id
                    activeId.value = it.id
                }, {}, {}, {})
            }
        }
        rule.onNodeWithText("Second source").performClick()
        assertEquals(listOf("second"), selections)
        rule.onNodeWithText("Second source").assertIsSelected()
        rule.onNodeWithText("First source").assertIsNotSelected()
    }

    @Test fun `external active source updates do not leave stale selection`() {
        val activeId = mutableStateOf<String?>("first")
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceSheet(selectableSources, activeId.value, {}, {}, {}, {})
            }
        }
        rule.runOnIdle { activeId.value = "second" }
        rule.onNodeWithText("Second source").assertIsSelected()
        rule.onNodeWithText("First source").assertIsNotSelected()
        rule.runOnIdle { activeId.value = null }
        rule.onNodeWithText("First source").assertIsNotSelected()
        rule.onNodeWithText("Second source").assertIsNotSelected()
    }

    @Test fun `removal action stays separate from source selection`() {
        var selected = 0
        var removed = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceSheet(selectableSources.take(1), "first", { selected++ }, { removed++ }, {}, {})
            }
        }
        rule.onNodeWithContentDescription("Remove playlist").performClick()
        assertEquals(0, selected)
        assertEquals(0, removed)
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, selected)
        assertEquals(0, removed)
        rule.onNodeWithContentDescription("Remove playlist").performClick()
        rule.onNode(hasText("Remove playlist") and hasClickAction()).performClick()
        assertEquals(0, selected)
        assertEquals(1, removed)
    }

    @Test fun `TV OK selects the focused source once and preserves focus`() {
        val activeId = mutableStateOf("first")
        val selections = mutableListOf<String>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                TvPresentation(true, remember { TvInputRegistry() }) {
                    PlaylistSourceSheet(selectableSources, activeId.value, {
                        selections += it.id
                        activeId.value = it.id
                    }, {}, {}, {})
                }
            }
        }
        val row = rule.onNodeWithText("Second source")
        row.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        row.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        row.assertIsSelected().assertIsFocused()
        assertEquals(listOf("second"), selections)
    }

    @Test fun `active source removal explains consequences and cancellation writes nothing`() {
        var removed = 0
        var dismissed = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceRemovalDialog("Fixture", true, { removed++ }, { dismissed++ })
            }
        }
        rule.onNodeWithText("Remove active playlist", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, removed)
        assertEquals(1, dismissed)
    }

    @Test fun `historical over-capacity sources remain accessible but cannot silently add another`() {
        val sources = List(20) {
            PlaylistSource("$it", PlaylistSourceType.URL, "https://example/$it", "Source $it", it.toLong())
        }
        var selected: PlaylistSource? = null
        var added = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceSheet(sources, "19", { selected = it }, {}, { added++ }, {})
            }
        }
        rule.onNodeWithText("Add playlist").assertIsDisplayed()
        rule.onNodeWithTag("playlist-source-list").performScrollToNode(hasText("Source 0"))
        rule.onNodeWithText("Source 0").assertIsDisplayed().performClick()
        assertEquals("0", selected?.id)
        rule.onNodeWithText("Add playlist").assertIsNotEnabled()
        assertEquals(0, added)
    }
}
