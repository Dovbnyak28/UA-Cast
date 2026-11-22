package app.zorik.player.ui.legal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h800dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class ThirdPartyLicensesUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun licenceCanBeOpenedAndClosedWithoutLeavingTheApp() {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) { ThirdPartyLicensesButton() } }
        rule.onNodeWithText("Third-party licences").performClick()
        rule.onNodeWithText("FFmpeg — LGPL 2.1+").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("GNU LESSER GENERAL PUBLIC LICENSE", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("GNU LESSER GENERAL PUBLIC LICENSE", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("FFmpeg — LGPL 2.1+").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("FFmpeg — LGPL 2.1+").assertDoesNotExist()
    }
}
