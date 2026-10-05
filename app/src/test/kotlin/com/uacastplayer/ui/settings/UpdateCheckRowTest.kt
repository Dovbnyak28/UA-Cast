package com.uacastplayer.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.update.AppVersion
import com.uacastplayer.update.GitHubRelease
import com.uacastplayer.update.ReleaseApk
import com.uacastplayer.update.UpdateCheckOutcome
import com.uacastplayer.update.UpdateInstallState
import com.uacastplayer.update.UpdateSectionState
import com.uacastplayer.update.UpdateUiState
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Whether the update Settings row actually offers the update it knows about.
 *
 * The offer used to hang off [UpdateUiState.lastOutcome], which is documented as "result of the
 * most recent **manual** check only" - the weekly automatic check leaves it null on purpose, so
 * that a check nobody asked for cannot report a failure. So an automatic check could raise the
 * banner in the top bar and then, on the one screen a user goes to about updates, show nothing but
 * the button that starts another check. The install this app can now perform was unreachable
 * without asking GitHub a second time for an answer it already had.
 *
 * The row is `internal` for this test. Composing the whole Settings screen to reach it would mean
 * fabricating forty-odd unrelated parameters, and the decision under test is entirely local to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class UpdateCheckRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    // The uk qualifier means resources resolve to Ukrainian, so labels are read from resources
    // rather than written out in English.
    private fun label(id: Int) = application.getString(id)

    private val apk = ReleaseApk(downloadUrl = "https://example.test/u.apk", sizeBytes = 40_000, sha256 = null)

    private fun release(withApk: Boolean = true) = GitHubRelease(
        version = requireNotNull(AppVersion.parse("0.9.1")),
        tagName = "v0.9.1",
        releaseUrl = "https://example.test/releases/v0.9.1",
        apk = if (withApk) apk else null,
    )

    private var installed: ReleaseApk? = null
    private var openedUrl: String? = null

    private fun show(state: UpdateUiState, install: UpdateInstallState = UpdateInstallState.Idle) {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Column {
                    UpdateCheckRow(
                        UpdateSectionState(
                            state = state,
                            installState = install,
                            onCheckNow = {},
                            onOpenRelease = { openedUrl = it },
                            onDownloadAndInstall = { installed = it },
                            onGrantInstallPermission = {},
                            onDismissBanner = {},
                            onOutcomeShown = {},
                        ),
                    )
                }
            }
        }
    }

    /** The bug: an automatic check leaves lastOutcome null, and the offer has to survive that. */
    @Test
    fun `an update found by the automatic check is still offered in settings`() {
        show(UpdateUiState(availableRelease = release(), lastOutcome = null))

        composeRule.onNodeWithText(label(R.string.settings_update_install_button)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.update_banner_action)).assertIsDisplayed()
    }

    /** The control: a manual check must not have lost the offer it always had. */
    @Test
    fun `an update found by a manual check is offered too`() {
        show(
            UpdateUiState(
                availableRelease = release(),
                lastOutcome = UpdateCheckOutcome.UPDATE_AVAILABLE,
            ),
        )

        composeRule.onNodeWithText(label(R.string.settings_update_install_button)).assertIsDisplayed()
    }

    @Test
    fun `the install button hands over the release's apk`() {
        show(UpdateUiState(availableRelease = release(), lastOutcome = null))

        composeRule.onNodeWithText(label(R.string.settings_update_install_button)).performClick()

        assertEquals(apk, installed)
    }

    /**
     * A release this build will not take an APK from - none attached, or more than one, which
     * `ReleaseApkPolicy` refuses to choose between. The page is then the whole offer, and it must
     * still be there.
     */
    @Test
    fun `a release with no usable apk still offers the page`() {
        show(UpdateUiState(availableRelease = release(withApk = false), lastOutcome = null))

        composeRule.onNodeWithText(label(R.string.update_banner_action)).performClick()

        assertEquals("https://example.test/releases/v0.9.1", openedUrl)
    }

    /** Nothing newer exists: the row says so and offers nothing to install. */
    @Test
    fun `being up to date offers no install`() {
        show(UpdateUiState(availableRelease = null, lastOutcome = UpdateCheckOutcome.UP_TO_DATE))

        composeRule.onNodeWithText(label(R.string.settings_update_up_to_date)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_update_install_button)).assertDoesNotExist()
    }

    /** A check in flight replaces the button with a spinner, and must not offer a stale release
     * underneath it. */
    @Test
    fun `nothing is offered while a check is running`() {
        show(UpdateUiState(isChecking = true, availableRelease = release(), lastOutcome = null))

        composeRule.onNodeWithText(label(R.string.settings_update_checking)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_update_install_button)).assertDoesNotExist()
    }

    /** Each install failure says its own thing, and only the retryable ones offer the button
     * again - see UpdateInstallState. */
    @Test
    fun `a refused signature is reported and not offered for retry`() {
        show(UpdateUiState(availableRelease = release(), lastOutcome = null), UpdateInstallState.Untrusted)

        composeRule.onNodeWithText(label(R.string.settings_update_untrusted)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_update_install_button)).assertDoesNotExist()
    }

    @Test
    fun `a damaged download is reported and can be retried`() {
        show(UpdateUiState(availableRelease = release(), lastOutcome = null), UpdateInstallState.Corrupt)

        composeRule.onNodeWithText(label(R.string.settings_update_corrupt)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_update_install_button)).assertIsDisplayed()
    }

    @Test
    fun `a missing permission offers the settings screen that grants it`() {
        show(UpdateUiState(availableRelease = release(), lastOutcome = null), UpdateInstallState.NeedsPermission)

        composeRule.onNodeWithText(label(R.string.settings_update_needs_permission)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.settings_update_grant_permission_button)).assertIsDisplayed()
    }
}
