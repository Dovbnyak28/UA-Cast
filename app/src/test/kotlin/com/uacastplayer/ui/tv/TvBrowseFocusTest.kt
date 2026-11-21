package com.uacastplayer.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.RetainedUnderlay
import com.uacastplayer.ui.home.HomeSourceState
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
@Config(qualifiers = "en-w960dp-h540dp-television-xhdpi")
@Category(RequiresComposeTestManifest::class)
class TvBrowseFocusTest {
    @get:Rule val rule = createComposeRule()
    private val channels = List(40) { M3uChannel("Channel $it", "https://example.test/$it", tvgId = "id$it") }

    @Test fun returnFromOpaquePlayerRestoresAnOffscreenChannel() {
        setBrowser(mutableStateOf(channels))
        rule.waitUntil { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_channel_grid").performScrollToIndex(25)
        rule.onNodeWithTag("tv_channel_25").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithTag("tv_channel_25").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("Exit player").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithTag("tv_channel_25").assertIsFocused()
    }

    @Test fun removedChannelFallsBackNearbyWithoutIndexErrors() {
        val dataset = mutableStateOf(channels)
        setBrowser(dataset)
        rule.waitUntil { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_channel_grid").performScrollToIndex(25)
        rule.onNodeWithTag("tv_channel_25").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithTag("tv_channel_25").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { dataset.value = channels.take(10) }
        rule.onNodeWithText("Exit player").performKeyInput { pressKey(Key.DirectionCenter) }
        // Focus is restored by an effect after the shorter grid lays out, one frame after the card
        // appears; asserting in the same frame raced it on a slow CI runner. Wait for the outcome.
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag("tv_channel_9").assertIsFocused() }.isSuccess
        }
        rule.onNodeWithTag("tv_channel_9").assertIsFocused()
    }

    @Test fun filteringKeepsInputFocusAndDoesNotRequestTheFirstCardAgain() {
        setBrowser(mutableStateOf(channels))
        rule.waitUntil { rule.onAllNodesWithTag("tv_channel_0").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tv_search").performClick().performTextInput("Channel 2")
        rule.onNodeWithTag("tv_search").assertIsFocused()
    }

    @Test fun duplicateIdentityPreservesTheSelectedOccurrenceAndReorderingFindsTheSameChannel() {
        val duplicate = channels[0].copy(displayName = "Duplicate")
        assertEquals(1, resolveTvFocusIndex(listOf(channels[0], duplicate), FavoriteKey.of(duplicate), 1))
        assertEquals(0, resolveTvFocusIndex(channels.reversed(), FavoriteKey.of(channels.last()), 39))
        assertEquals(0, resolveTvFocusIndex(emptyList(), "missing", 999))
    }

    private fun setBrowser(dataset: androidx.compose.runtime.MutableState<List<M3uChannel>>) {
        rule.setContent { UaCastTheme(AppTheme.CINEMA) {
            TvPresentation(true, remember { TvInputRegistry() }) {
                var playing by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalTvBrowseActive provides !playing) {
                        RetainedUnderlay(covered = { playing }, modifier = Modifier.fillMaxSize()) {
                            TvBrowseScreen(BottomDestination.CHANNELS, PlaylistUiState(channels = dataset.value),
                                HomeSourceState(emptyList(), null, {}, {}, {}, {}), emptyList(), emptySet(), { null },
                                { _, _ -> playing = true })
                        }
                    }
                    if (playing) {
                        val focus = remember { FocusRequester() }
                        Button(onClick = { playing = false }, modifier = Modifier.focusRequester(focus)) {
                            Text("Exit player")
                        }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    }
                }
            }
        } }
    }
}
