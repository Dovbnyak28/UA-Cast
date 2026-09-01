package com.uacastplayer.ui.nav

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.UiTestTags
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import com.uacastplayer.update.UpdateSectionState
import com.uacastplayer.update.UpdateInstallState
import com.uacastplayer.update.UpdateUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The download banner used to be an overlay aligned to the top of a Box that also held the whole
 * scaffold, so it was painted over the screen title rather than making room for it: on a device
 * with a playlist loading, "UA Cast Player" and Settings' first section header were visibly cut in
 * half. Moving it into [RootTopBar] fixes that, and this is the assertion that keeps it fixed - the
 * bug is a pure layout fact (two sibling bounds overlapping), so it can be checked exactly rather
 * than left to whoever next looks at a screenshot.
 *
 * Deliberately about *bounds*, not pixels. A golden image would also have caught the original bug,
 * but only if a human noticed the clipped text in the recorded image; this fails with the two
 * rectangles printed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class RootTopBarLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeDownloadBanner_doesNotOverlapTheTitle() {
        setTopBar(downloading = true)

        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertIsDisplayed()
        val banner = composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithTag(UiTestTags.ROOT_TOP_BAR_TITLE).getUnclippedBoundsInRoot()

        assertTrue(
            "Banner (bottom=${banner.bottom}) overlaps the title row (top=${title.top})",
            banner.bottom <= title.top,
        )
    }

    /**
     * The other half of "pushes instead of covering": the push has to be temporary. An
     * [androidx.compose.animation.AnimatedVisibility] whose hidden state still reserved the
     * banner's height would leave the title sitting that far down forever, which no screenshot
     * taken during a download would reveal.
     */
    @Test
    fun whenTheDownloadFinishes_theTitleMovesBackUp() {
        val downloading = setTopBar(downloading = true)
        val whileDownloading = composeRule.onNodeWithTag(UiTestTags.ROOT_TOP_BAR_TITLE).getUnclippedBoundsInRoot()

        composeRule.runOnIdle { downloading.value = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertDoesNotExist()
        val afterwards = composeRule.onNodeWithTag(UiTestTags.ROOT_TOP_BAR_TITLE).getUnclippedBoundsInRoot()
        assertTrue(
            "Title stayed at ${afterwards.top} after the banner went away (was ${whileDownloading.top})",
            afterwards.top < whileDownloading.top,
        )
    }

    @Test
    fun guideWorkWithoutAPlaylist_doesNotCoverTheEmptyStateWithAChannelBanner() {
        setTopBar(downloading = true, showDownloadStatus = false)

        composeRule.onNodeWithTag(UiTestTags.DOWNLOAD_STATUS_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(UiTestTags.ROOT_TOP_BAR_TITLE).assertIsDisplayed()
    }

    @Test
    fun downloadStatusRequiresPlaylistContentOrAnActivePlaylistLoad() {
        assertFalse(shouldShowDownloadStatus(PlaylistUiState()))
        assertTrue(shouldShowDownloadStatus(PlaylistUiState(isLoading = true)))
        assertTrue(
            shouldShowDownloadStatus(
                PlaylistUiState(channels = listOf(M3uChannel("Channel", "https://example.test/live"))),
            ),
        )
    }

    /** Returns the flag driving the banner so a test can switch the download off mid-composition. */
    private fun setTopBar(
        downloading: Boolean,
        showDownloadStatus: Boolean = true,
    ): MutableState<Boolean> {
        val state = mutableStateOf(downloading)
        composeRule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                RootTopBar(
                    title = "UA Cast Player",
                    iconPrefetchState = IconPrefetchUiState(
                        isRunning = state.value,
                        completed = 42,
                        total = 120,
                    ),
                    epgState = EpgUiState(isLoading = state.value),
                    showDownloadStatus = showDownloadStatus,
                    // No update to announce: this test is about the download banner's effect on
                    // the title row's height, and a second banner would change what it measures.
                    updateSection = UpdateSectionState(
                        state = UpdateUiState(),
                        installState = UpdateInstallState.Idle,
                        onCheckNow = {},
                        onOpenRelease = {},
                        onDownloadAndInstall = {},
                        onGrantInstallPermission = {},
                        onDismissBanner = {},
                        onOutcomeShown = {},
                    ),
                )
            }
        }
        return state
    }
}
