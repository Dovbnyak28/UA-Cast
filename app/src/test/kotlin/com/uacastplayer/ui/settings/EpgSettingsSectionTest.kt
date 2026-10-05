package com.uacastplayer.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.epg.EpgSource
import com.uacastplayer.epg.EpgUiState
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
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class EpgSettingsSectionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun customSourceDoesNotPretendThatTheDefaultPresetIsSelected() {
        var selected: EpgSource? = null
        render(EpgUiState(customUrl = "https://example.test/guide.xml")) { selected = it }
        rule.onNodeWithText(label(R.string.settings_epg_custom_active)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.settings_epg_source_label)).performClick()
        rule.onNodeWithText("it999 · epg2.xml.gz").assertIsNotSelected().performClick()
        assertEquals(EpgSource.DEFAULT, selected)
    }

    @Test fun loadingIsNotReportedAsAnEmptyGuide() {
        render(EpgUiState(isLoading = true))
        rule.onNodeWithText(label(R.string.epg_guide_loading)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.epg_guide_no_data)).assertDoesNotExist()
    }

    @Test fun failureIsNotReportedAsAnEmptyGuide() {
        render(EpgUiState(hasError = true))
        rule.onNodeWithText(label(R.string.epg_guide_error)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.epg_guide_no_data)).assertDoesNotExist()
    }

    private fun render(state: EpgUiState, onSelected: (EpgSource) -> Unit = {}) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column { EpgSettingsSection(state, onSelected, {}) }
            }
        }
    }

    private fun label(resource: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resource)
}
