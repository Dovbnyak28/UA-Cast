package com.uacastplayer.ui.channels

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w320dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class CinemaCatalogInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // Different programme identities can legitimately share an upstream stream URL.
    private val channels = listOf(
        M3uChannel(displayName = "First", streamUrl = SHARED_URL, tvgId = "first"),
        M3uChannel(displayName = "Second", streamUrl = SHARED_URL, tvgId = "second"),
    )
    private var selectedChannels: List<M3uChannel>? = null
    private var selectedIndex: Int? = null
    private var selectionCalls = 0
    private var catalogVisible by mutableStateOf(true)

    @Test
    fun selectingTheSecondChannelWithASharedUrlKeepsItsPlaylistIndex() {
        showCatalog(ChannelLayout.LIST)
        openGroup()

        rule.onNodeWithText("Second").assertIsDisplayed().performClick()

        rule.runOnIdle {
            assertEquals(1, selectionCalls)
            assertSame("Playback must receive the original flat playlist", channels, selectedChannels)
            assertEquals("The selected channel must not resolve to the first matching URL", 1, selectedIndex)
        }
    }

    @Test
    fun listGuideActionOpensTheSelectedChannelsGuideWithoutStartingPlayback() {
        assertDirectGuideAction(ChannelLayout.LIST)
    }

    @Test
    fun gridGuideActionOpensTheSelectedChannelsGuideWithoutStartingPlayback() {
        assertDirectGuideAction(ChannelLayout.GRID)
    }

    @Test
    fun groupSearchSurvivesReturningToOverviewAndReopeningTheGroup() {
        showCatalog(ChannelLayout.LIST)
        openGroup()
        rule.onNode(hasSetTextAction()).performTextInput("Second")
        awaitOnlySecondResult()

        rule.onNodeWithContentDescription("Back").performClick()
        openGroup()

        rule.onNode(hasSetTextAction()).assertTextEquals("Second")
        awaitOnlySecondResult()
    }

    @Test
    fun globalSearchSurvivesReturningFromThePlaybackDestination() {
        showCatalog(ChannelLayout.LIST, navigateToPlaybackOnSelection = true)
        rule.onNode(hasSetTextAction()).performTextInput("Second")
        awaitOnlySecondResult()
        rule.onNode(secondResult()).performClick()
        rule.onNodeWithText(PLAYBACK_DESTINATION).assertIsDisplayed()

        // SaveableStateHolder models the destination retention provided by the real NavHost.
        rule.runOnIdle { catalogVisible = true }

        rule.onNode(hasSetTextAction()).assertTextEquals("Second")
        awaitOnlySecondResult()
        rule.runOnIdle { assertEquals(1, selectionCalls) }
    }

    @Test
    fun globalSearchGuideActionOpensTheGuideWithoutStartingPlayback() {
        showCatalog(ChannelLayout.LIST)
        rule.onNode(hasSetTextAction()).performTextInput("Second")
        awaitOnlySecondResult()

        clickSecondGuide()

        assertSecondGuideDoesNotStartPlayback()
    }

    private fun assertDirectGuideAction(layout: ChannelLayout) {
        showCatalog(layout)
        openGroup()

        // A normal tap must reach the schedule without a hidden long-press gesture.
        clickSecondGuide()

        assertSecondGuideDoesNotStartPlayback()
    }

    private fun assertSecondGuideDoesNotStartPlayback() {
        rule.onNode(hasText("Second") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNodeWithText("No TV guide for this channel").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next").assertIsDisplayed()
        rule.runOnIdle { assertEquals("Opening EPG must not start playback", 0, selectionCalls) }
    }

    private fun clickSecondGuide() {
        // Item entry starts after a coroutine delay, which is not itself a Compose idling resource.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.channels_channel_guide) + ": Second")
            .assertIsDisplayed().performClick()
    }

    private fun secondResult() = hasText("Second") and !hasSetTextAction()

    private fun awaitOnlySecondResult() {
        rule.waitUntil(5_000) {
            rule.onAllNodes(secondResult()).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("First").fetchSemanticsNodes().isEmpty()
        }
        rule.onNode(secondResult()).assertIsDisplayed()
    }

    private fun openGroup() {
        rule.onNodeWithText(GROUP_NAME).assertIsDisplayed().performClick()
    }

    private fun showCatalog(layout: ChannelLayout, navigateToPlaybackOnSelection: Boolean = false) {
        val state = PlaylistUiState(
            groups = listOf(GroupedChannels(ChannelGroup.Custom(GROUP_NAME), channels)),
            channels = channels,
        )
        rule.setContent {
            val destinationState = rememberSaveableStateHolder()
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.fillMaxSize()) {
                    if (catalogVisible) {
                        destinationState.SaveableStateProvider("catalog") {
                            ChannelsScreen(
                                playlistState = state,
                                onChannelSelected = { playlist, index ->
                                    selectedChannels = playlist
                                    selectedIndex = index
                                    selectionCalls++
                                    if (navigateToPlaybackOnSelection) catalogVisible = false
                                },
                                epgState = EpgUiState(nowMillis = 1_700_000_000_000L),
                                iconPrefetchState = IconPrefetchUiState(),
                                resolveIcon = { null },
                                density = ListDensity.FULL,
                                layout = layout,
                                onChannelLayoutSelected = {},
                                isFavorite = { false },
                                onToggleFavorite = {},
                                isChannelLocked = { false },
                                onLockChannel = {},
                                onUnlockChannel = {},
                                onRefreshPlaylist = {},
                                showIconTierBanner = false,
                                onEnableIcons = {},
                                onDismissIconTierBanner = {},
                                pinnedGroupKeys = emptySet(),
                                hiddenGroupKeys = emptySet(),
                                onPinGroup = {},
                                onHideGroup = {},
                                onClearGroupOverride = {},
                                onOpenAddPlaylist = {},
                            )
                        }
                    } else {
                        Text(PLAYBACK_DESTINATION, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    private companion object {
        const val GROUP_NAME = "Shared stream group"
        const val SHARED_URL = "https://example.test/shared-stream.m3u8"
        const val PLAYBACK_DESTINATION = "Playback destination"
    }
}
