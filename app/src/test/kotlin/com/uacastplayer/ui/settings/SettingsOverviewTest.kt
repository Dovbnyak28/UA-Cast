package com.uacastplayer.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class SettingsOverviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchFiltersSettingsSections() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                SettingsOverview(
                    onOpenGeneral = {},
                    onOpenPlaylist = {},
                    onOpenPlayback = {},
                    onOpenData = {},
                    onOpenSupport = {},
                    searchQuery = "playlist",
                )
            }
        }

        composeRule.onNodeWithText("Playlist & access").assertIsDisplayed()
        composeRule.onAllNodesWithText("Channel layout, icons, buffer, and navigation").assertCountEquals(0)
    }

    @Test
    fun searchShowsAnActionableNoResultsState() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                SettingsOverview(
                    onOpenGeneral = {},
                    onOpenPlaylist = {},
                    onOpenPlayback = {},
                    onOpenData = {},
                    onOpenSupport = {},
                    searchQuery = "not-a-setting",
                )
            }
        }

        composeRule.onNodeWithText("No settings match “not-a-setting”").assertIsDisplayed()
    }
}
