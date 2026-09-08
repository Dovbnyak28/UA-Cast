package com.uacastplayer.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import com.uacastplayer.R
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
    fun wifiSearchOpensTheActualControlNotOnlyASection() {
        var selected: SettingsSearchEntry? = null
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                SettingsOverview({}, {}, {}, {}, {}, searchQuery = "wi fi", onOpenSetting = { selected = it })
            }
        }
        composeRule.onNodeWithText("Fetch channel logos on Wi-Fi only").assertIsDisplayed().performClick()
        assertEquals(R.string.settings_icon_wifi_only_label, selected?.labelRes)
        assertEquals(SettingsPage.GENERAL, selected?.page)
    }

    @Test
    fun parentalControlHasItsOwnDestination() {
        var opened = false
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column {
                    SettingsOverview({}, {}, {}, {}, {}, onOpenParental = { opened = true })
                }
            }
        }
        composeRule.onNodeWithText("Parental control").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun epgSearchResolvesToThePlaylistPage() {
        var selected: SettingsSearchEntry? = null
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column {
                    SettingsOverview({}, {}, {}, {}, {}, searchQuery = "XMLTV", onOpenSetting = { selected = it })
                }
            }
        }
        composeRule.onNodeWithText("TV guide source").performClick()
        assertEquals(SettingsPage.PLAYLIST, selected?.page)
    }

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

        composeRule.onNodeWithText("Add playlist").assertIsDisplayed()
        composeRule.onAllNodesWithText("Buffer, channel switching, and battery behavior").assertCountEquals(0)
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
