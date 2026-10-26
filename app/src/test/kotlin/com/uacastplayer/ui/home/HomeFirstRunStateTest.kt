package com.uacastplayer.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class HomeFirstRunStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyHomeExplainsThatChannelsComeFromTheUserAndOffersTheNextAction() {
        var addPlaylistRequests = 0
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.size(width = 411.dp, height = 891.dp)) {
                    HomeScreen(
                        content = HomeContentState(
                            playlistState = PlaylistUiState(),
                            epgState = EpgUiState(nowMillis = 0L),
                            iconPrefetchState = IconPrefetchUiState(),
                            favorites = emptyList(),
                            lastWatchedChannelKey = null,
                        ),
                        source = HomeSourceState(
                            playlistSources = emptyList(),
                            activePlaylistSourceId = null,
                            onSwitchPlaylistSource = {},
                            onRemovePlaylistSource = {},
                            onOpenAddPlaylist = { addPlaylistRequests++ },
                            onRefreshPlaylist = {},
                        ),
                        resolveIcon = { null },
                        onChannelSelected = { _, _ -> },
                        onOpenChannels = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.home_empty_message)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_empty_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_add_playlist_button))
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, addPlaylistRequests)
    }
}
