package app.zorik.player.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A search result has to land on its control, not only on its page.
 *
 * "TV guide source" (also found as XMLTV / EPG) opened the Playlist page and left the field where
 * it was. On a phone in portrait it happened to be on screen; in landscape it sat below the fold,
 * unmarked, and the instrumented settings-search journey failed there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class SettingsSearchTargetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchingTheGuideSourceBringsItsFieldIntoView() {
        composeRule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(
                    LocalSettingsSearchTarget provides stringResource(R.string.settings_epg_source_label),
                ) {
                    // A page shorter than its content, with the field below the fold.
                    Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(1_000.dp))
                        EpgSourceEditor(currentUrl = null, hasError = false, onApply = {})
                    }
                }
            }
        }
        composeRule.onNodeWithText("TV guide source").assertIsDisplayed()
    }
}
