package com.uacastplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.update.AppVersion
import com.uacastplayer.update.GitHubRelease
import com.uacastplayer.update.ReleaseApk
import com.uacastplayer.update.UpdateInstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The update banner is the entire "notify" half of the update feature - if it does not appear, or
 * appears and cannot be acted on, nothing else in the feature matters.
 *
 * The viewport is deliberately the tight one from `FontScaleLayoutTest`: a 4.5" phone, which is
 * also what any phone becomes at the largest Display Size setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class UpdateBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** No APK attached, so the banner falls back to offering the release page - which is what the
     * assertions below about "Відкрити реліз" are about. */
    private val release = GitHubRelease(
        version = AppVersion.parse("v1.4.0")!!,
        tagName = "v1.4.0",
        releaseUrl = "https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.4.0",
    )

    private val apk = ReleaseApk(
        downloadUrl = "https://github.com/Dovbnyak28/UA-Cast/releases/download/v1.4.0/uacast.apk",
        sizeBytes = 40_000_000,
        sha256 = null,
    )

    @Composable
    private fun Banner(
        release: GitHubRelease?,
        installState: UpdateInstallState = UpdateInstallState.Idle,
        onInstall: (ReleaseApk) -> Unit = {},
        onOpen: (String) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = UpdateBanner(
        release = release,
        installState = installState,
        onInstall = onInstall,
        onOpen = onOpen,
        onDismiss = onDismiss,
    )

    @Test
    fun nothingIsShownWhenThereIsNoUpdate() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Banner(release = null)
            }
        }

        composeRule.onNodeWithTag(UiTestTags.UPDATE_BANNER).assertDoesNotExist()
    }

    @Test
    fun theVersionIsNamedSoTheUserKnowsWhatTheyAreBeingOffered() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Banner(release = release)
            }
        }

        composeRule.onNodeWithTag(UiTestTags.UPDATE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Доступна нова версія").assertIsDisplayed()
        composeRule.onNodeWithText("Версію v1.4.0 можна завантажити.").assertIsDisplayed()
    }

    /**
     * The bug this banner had for as long as it existed: its one button opened the release page.
     *
     * That page's most prominent downloads are GitHub's own source archives - a zip and a tar.gz of
     * the code - neither of which is installable, and the first of which is what a phone browser
     * offers to save. So the only action the app offered about an update led to a file that cannot
     * be one. When the release has an APK, the button installs it.
     */
    @Test
    fun theActionInstallsTheApkWhenTheReleaseHasOne() {
        var installed: ReleaseApk? = null
        var opened: String? = null
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Banner(release = release.copy(apk = apk), onInstall = { installed = it }, onOpen = { opened = it })
            }
        }

        composeRule.onNodeWithText("Завантажити й встановити").performClick()

        assertEquals(apk, installed)
        assertEquals("the page must not be opened as well", null, opened)
    }

    /** Progress is reported here rather than the button appearing to do nothing - a 40MB download
     * on a phone connection is minutes, and the banner is where the user pressed. */
    @Test
    fun aRunningDownloadIsReportedInTheBanner() {
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Banner(
                    release = release.copy(apk = apk),
                    installState = UpdateInstallState.Downloading(bytesSoFar = 10_000_000, totalBytes = 40_000_000),
                )
            }
        }

        composeRule.onNodeWithText("Завантаження… 25%").assertIsDisplayed()
    }

    /**
     * The fallback, and the control for the test above: a release this build will not take an APK
     * from (none attached, or several - see `ReleaseApkPolicy`) still offers the page, because then
     * a human reading it is the only way through.
     */
    @Test
    fun theActionOpensThatReleasesOwnPage() {
        var opened: String? = null
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Banner(release = release, onOpen = { opened = it })
            }
        }

        composeRule.onNodeWithText("Відкрити реліз").performClick()

        assertEquals("https://github.com/Dovbnyak28/UA-Cast/releases/tag/v1.4.0", opened)
    }

    @Test
    fun closingReportsTheDismissalExactlyOnce() {
        var dismissals = 0
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Banner(release = release, onDismiss = { dismissals++ })
            }
        }

        composeRule.onNodeWithContentDescription("Приховати").performClick()

        assertEquals(1, dismissals)
    }

    /** The banner is part of the layout rather than an overlay, so it pushes the screen down - and
     * at 2.0x text on a small screen its own action must still be reachable inside the viewport. */
    @Test
    fun theActionStaysOnScreenAtTheLargestFontScale() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2.0f)) {
                UaCastTheme(AppTheme.CINEMA) {
                    Banner(release = release)
                }
            }
        }

        val button = composeRule.onNodeWithText("Відкрити реліз")
        button.assertIsDisplayed()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bounds = button.getUnclippedBoundsInRoot()
        val slack = 0.5.dp
        assertTrue(
            "the update action is cut off at 2.0x: $bounds vs viewport $root",
            bounds.left >= root.left - slack &&
                bounds.top >= root.top - slack &&
                bounds.right <= root.right + slack &&
                bounds.bottom <= root.bottom + slack,
        )
    }

    /** Content is read from the last non-null release while the container animates away; reading
     * the incoming null instead blanked the text out for the length of the exit animation. */
    @Test
    fun theTextSurvivesTheReleaseBeingClearedForTheExitAnimation() {
        val current = mutableStateOf<GitHubRelease?>(release)
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Banner(release = current.value)
            }
        }
        composeRule.onNodeWithText("Версію v1.4.0 можна завантажити.").assertIsDisplayed()

        composeRule.mainClock.autoAdvance = false
        current.value = null
        composeRule.mainClock.advanceTimeByFrame()

        // Mid-exit: still the old text, not an empty line.
        composeRule.onNodeWithText("Версію v1.4.0 можна завантажити.").assertExists()
    }
}
