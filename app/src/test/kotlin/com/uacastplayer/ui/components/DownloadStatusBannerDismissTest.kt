package com.uacastplayer.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Whether dismissing the download banner sticks.
 *
 * Its contract is written down in its own KDoc: "Dismissing only hides it for the current download;
 * it reappears the next time icon prefetch or an EPG load starts". The effect that implements that
 * was keyed on the two flags separately, so it re-ran whenever *either* changed while the other was
 * still true - and re-running while anything was active cleared the dismissal.
 *
 * Which means a download *ending* undid a dismissal. That is not a hypothetical combination: the
 * banner exists for the moment a playlist is first added, and that is exactly when the icon
 * prefetch and the guide download run together. Dismiss it, wait for whichever finishes first, and
 * it comes back on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class DownloadStatusBannerDismissTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var iconState by mutableStateOf(IconPrefetchUiState())
    private var epgState by mutableStateOf(EpgUiState())

    private fun showBanner() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                DownloadStatusBanner(iconPrefetchState = iconState, epgState = epgState)
            }
        }
    }

    private fun bothDownloading() {
        iconState = IconPrefetchUiState(isRunning = true, completed = 10, total = 100)
        epgState = EpgUiState(isLoading = true)
    }

    /** Read from resources rather than written out: this test runs under Ukrainian qualifiers, so
     * the literal English label is not what is on screen. */
    private val dismissLabel: String
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.download_banner_dismiss)

    private fun dismiss() {
        composeRule.onNodeWithContentDescription(dismissLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertDoesNotExist()
    }

    /** The bug: one of two concurrent downloads finishing is not a reason to un-dismiss. */
    @Test
    fun `a dismissal survives the guide download finishing while icons carry on`() {
        bothDownloading()
        showBanner()
        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertExists()

        dismiss()

        epgState = EpgUiState(isLoading = false)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertDoesNotExist()
    }

    /** The mirror image, so this is pinned from both sides rather than for one flag by luck. */
    @Test
    fun `a dismissal survives the icon prefetch finishing while the guide carries on`() {
        bothDownloading()
        showBanner()

        dismiss()

        iconState = IconPrefetchUiState(isRunning = false)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertDoesNotExist()
    }

    /**
     * The control, and the reason the two above mean "the dismissal held" rather than "the banner
     * never comes back": a genuinely new download does undo it, which is the documented contract.
     */
    @Test
    fun `a new download after everything finished brings the banner back`() {
        bothDownloading()
        showBanner()

        dismiss()

        iconState = IconPrefetchUiState(isRunning = false)
        epgState = EpgUiState(isLoading = false)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertDoesNotExist()

        epgState = EpgUiState(isLoading = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertExists()
    }
}
