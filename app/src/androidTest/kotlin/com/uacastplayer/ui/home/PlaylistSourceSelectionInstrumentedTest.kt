package com.uacastplayer.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uacastplayer.R
import com.uacastplayer.playlist.PlaylistSource
import com.uacastplayer.playlist.PlaylistSourceType
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses synthetic sources only: does not import, delete or replace the user's playlists. */
@RunWith(AndroidJUnit4::class)
class PlaylistSourceSelectionInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val sources = listOf(
        PlaylistSource("first", PlaylistSourceType.URL, "https://example/first", "First source", 1),
        PlaylistSource("second", PlaylistSourceType.URL, "https://example/second", "Second source", 2),
    )

    @Test fun sourceSelectionIsAccessibleAndUpdatesOnce() {
        val activeId = mutableStateOf<String?>("first")
        val selections = mutableListOf<String>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceSheet(sources, activeId.value, {
                    selections += it.id
                    activeId.value = it.id
                }, {}, {}, {})
            }
        }
        rule.onNodeWithText("First source").assertIsSelected()
        rule.onNodeWithText("Second source").assertIsNotSelected().performClick()
        rule.onNodeWithText("Second source").assertIsSelected()
        rule.onNodeWithText("First source").assertIsNotSelected()
        assertEquals(listOf("second"), selections)
        rule.runOnIdle { activeId.value = null }
        rule.onNodeWithText("First source").assertIsNotSelected()
        rule.onNodeWithText("Second source").assertIsNotSelected()
    }

    @Test fun removingSourceDoesNotSelectItOrSkipConfirmation() {
        var selections = 0
        val removals = mutableListOf<String>()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaylistSourceSheet(sources.take(1), "first", { selections++ }, { removals += it.id }, {}, {})
            }
        }
        val removeLabel = rule.activity.getString(R.string.home_playlist_source_remove)
        rule.onNodeWithContentDescription(removeLabel).performClick()
        assertEquals(0, selections)
        assertEquals(emptyList<String>(), removals)
        rule.onNodeWithText(rule.activity.getString(R.string.common_cancel)).performClick()
        rule.onNodeWithText("First source").assertIsSelected()
        rule.onNodeWithContentDescription(removeLabel).performClick()
        rule.onNode(hasText(removeLabel) and hasClickAction()).performClick()
        assertEquals(0, selections)
        assertEquals(listOf("first"), removals)
    }
}
