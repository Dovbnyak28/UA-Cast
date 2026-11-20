package com.uacastplayer.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.ui.premium.FeatureGate
import com.uacastplayer.ui.premium.LocalFeatureGate
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

    @Test fun userCanReplaceCustomUrlWithoutAnyPublisherPresets() {
        var selected: String? = null
        render(EpgUiState(customUrl = "https://example.test/guide.xml")) { selected = it }
        rule.onNodeWithText("it999 · epg2.xml.gz").assertDoesNotExist()
        rule.onNodeWithText(label(R.string.settings_epg_source_label))
            .performTextReplacement("https://example.test/new.xml")
        rule.onNodeWithText(label(R.string.settings_epg_apply)).performClick()
        assertEquals("https://example.test/new.xml", selected)
    }

    @Test fun invalidAddressCannotBeApplied() {
        render(EpgUiState())
        rule.onNodeWithText(label(R.string.settings_epg_source_label)).performTextReplacement("file:///private.xml")
        rule.onNodeWithText(label(R.string.settings_epg_apply)).assertIsNotEnabled()
        rule.onNodeWithText(label(R.string.add_playlist_url_error)).assertIsDisplayed()
    }

    @Test fun failedSourceCanBeRetriedWithoutEditingItsAddress() {
        var selected: String? = null
        val url = "https://example.test/guide.xml"
        render(EpgUiState(customUrl = url, hasError = true),
            FeatureGate({ false }, { error("Retrying an existing source must not open a paywall") })) { selected = it }
        rule.onNodeWithText(label(R.string.common_retry)).assertIsDisplayed().performClick()
        assertEquals(url, selected)
    }

    @Test fun changingTheProviderStillRequiresTheExistingEntitlement() {
        var paywalls = 0
        var applied: String? = null
        render(EpgUiState(), FeatureGate({ false }, { paywalls++ })) { applied = it }
        rule.onNodeWithText(label(R.string.settings_epg_source_label))
            .performTextReplacement("https://example.test/guide.xml")
        rule.onNodeWithText(label(R.string.settings_epg_apply)).performClick()
        assertEquals(1, paywalls)
        assertEquals(null, applied)
    }

    @Test fun clearingAddressDisablesGuide() {
        var selected: String? = null
        render(EpgUiState(customUrl = "https://example.test/guide.xml")) { selected = it }
        rule.onNodeWithText(label(R.string.settings_epg_source_label)).performTextReplacement("")
        rule.onNodeWithText(label(R.string.settings_epg_apply)).performClick()
        assertEquals("", selected)
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

    private fun render(state: EpgUiState, gate: FeatureGate = FeatureGate.Open, onSelected: (String) -> Unit = {}) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalFeatureGate provides gate) {
                    Column { EpgSettingsSection(state, onSelected, {}) }
                }
            }
        }
    }

    private fun label(resource: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resource)
}
