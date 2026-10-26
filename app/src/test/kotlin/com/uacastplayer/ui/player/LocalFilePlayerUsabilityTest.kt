package com.uacastplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.dlna.DlnaConnectionState
import com.uacastplayer.media.LocalVideo
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SleepTestApplication
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = SleepTestApplication::class, qualifiers = "en-w360dp-h740dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalFilePlayerUsabilityTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private val video = LocalVideo("content://media/video/1",
        "A long family holiday video name that should stay readable.mp4", "video/mp4")
    private val channel = M3uChannel(video.displayName, video.uri, localVideo = video)
    private val state = PlayerUiState(currentChannel = channel, isBuffering = false, canControlPlayback = true)
    @After fun close() { store.clear() }

    @Test fun fileInlineDoesNotOfferTelevisionNavigation() {
        val app = ApplicationProvider.getApplicationContext<SleepTestApplication>()
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[PlayerViewModel::class.java]
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                InlinePlayerContent(
                    PlayerScreenContent(state, DlnaConnectionState(), 0, AspectRatioFrameLayout.RESIZE_MODE_FIT),
                    PlayerScreenActions(vm, {}, { false }, {}, { null }, {}),
                    PlayerScreenTransientState(null, null),
                )
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/file_player_inline_cinema.png")
        rule.onNodeWithText("TV guide").assertDoesNotExist()
        rule.onNodeWithText("Previous channel").assertDoesNotExist()
        rule.onNodeWithText("Next channel").assertDoesNotExist()
        rule.onNodeWithText("Back to channels").assertDoesNotExist()
    }

    @Test fun fileOverlayIsNotLabeledAsLiveTelevision() {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerControlsOverlay(state, true, remember { mutableStateOf(null) }, {}, {}, {}, {}, {}, {}, {},
                        false, {}, {}, {}, fileProgress = {
                            FilePlaybackProgress(FilePlaybackTimeline(65_000, 3_661_000), true, {},
                                Modifier.padding(horizontal = 20.dp))
                        })
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/file_player_overlay_cinema.png")
        rule.onNodeWithText("LIVE").assertDoesNotExist()
        rule.onNodeWithText("1:05").assertIsDisplayed()
        rule.onNodeWithContentDescription("Video position").assertIsDisplayed()
    }

    @Test fun videoFilesNeverSearchTheChannelLogoPack() {
        val lookups = AtomicInteger()
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                ChannelInfoCard(channel, PlaybackBadgesState(), 0) { lookups.incrementAndGet(); null }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, lookups.get()) }
    }

    @Test fun fileFailureOffersRetryAndBackButNotAnotherChannel() {
        var exits = 0
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                PlaybackFailureCard({}, {}, { exits++ }, localVideo = true, canGoNext = false)
            }
        }
        rule.onNodeWithText("Next channel").assertDoesNotExist()
        rule.onNodeWithText("Back").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun aLargeTextTimelineIsReadableAndAccessibleSeekingCommitsOnePosition() {
        var target = -1L
        rule.setContent {
            val density = LocalDensity.current
            UaCastTheme(AppTheme.CINEMA) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Column(Modifier.fillMaxSize().background(com.uacastplayer.ui.theme.UaTheme.palette.void)
                        .padding(20.dp)) {
                        FileInfoCard(video, PlaybackBadgesState())
                        FilePlaybackProgress(FilePlaybackTimeline(65_000, 3_661_000), true, { target = it })
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/file_player_large_text_cinema.png")
        rule.onNodeWithText("1:05").assertIsDisplayed()
        rule.onNodeWithText("1:01:01").assertIsDisplayed()
        rule.onNodeWithContentDescription("Video position").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        rule.runOnIdle { assertEquals(1_830_500L, target) }
    }
}
