package com.uacastplayer.ui.favorites

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.favorites.FavoriteRemovalUndo
import com.uacastplayer.favorites.FavoritesSortOrder
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class FavoritesUsabilityTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val a = FavoriteChannel("a", "Alpha", "https://example.test/a", null, null, 42)

    @Test fun searchCanBeClearedWithOneTapAndNoResultsOfferRecovery() {
        setScreen(listOf(a, a.copy(key = "b", displayName = "Beta")))
        rule.onNodeWithTag("favorites_search").performTextInput("missing")
        rule.onNodeWithText(context.getString(R.string.player_channels_no_results)).assertIsDisplayed()
        rule.onRoot().captureRoboImage("src/test/screenshots/favorites_search_empty.png")
        rule.onNodeWithContentDescription(context.getString(R.string.channels_clear_search)).performClick()
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test fun deletingTheLastFavoriteKeepsUndoAvailableAndRestoresItsData() {
        val owner = setScreen(listOf(a))
        rule.onNodeWithText(context.getString(R.string.favorites_edit)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.favorites_remove_content_description))
            .performClick()
        rule.onNodeWithText(context.getString(R.string.favorites_remove_content_description)).performClick()
        rule.onNodeWithText(context.getString(R.string.common_undo)).assertIsDisplayed()
        rule.onRoot().captureRoboImage("src/test/screenshots/favorite_undo_last.png")
        rule.onNodeWithText(context.getString(R.string.common_undo)).performClick()
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        assertEquals(null, owner.notice.value)
    }

    private fun setScreen(initial: List<FavoriteChannel>): FavoriteRemovalUndo {
        val items = mutableStateOf(initial)
        val owner = FavoriteRemovalUndo({ items.value }, { key ->
            items.value = items.value.filterNot { it.key == key }
        }, { item, index -> items.value = items.value.toMutableList().apply { add(index, item) } })
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            val notice by owner.notice.collectAsState()
            val section = FavoriteUndoSection(notice, owner::undo, owner::dismiss)
            CompositionLocalProvider(LocalFavoriteUndo provides section) {
                Scaffold(snackbarHost = { FavoriteUndoSnackbar() }) { padding ->
                    FavoritesScreen(items.value, emptyList(), FavoritesSortOrder.MANUAL, {}, { _, _ -> },
                        owner::removeWithUndo, { items.value = it }, {}, { null }, Modifier.padding(padding))
                }
            }
        } }
        return owner
    }
}
