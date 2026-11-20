@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.uacastplayer.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerVideoFrameListenerTest {
    private val state = MutableStateFlow(PlayerUiState())
    private val listener = PlayerVideoFrameListener(state)
    private val first = MediaItem.fromUri("https://example/first.mp4")
    private val second = MediaItem.fromUri("https://example/second.mp4")

    @Test fun preparingAndReadyDoNotClaimThatAVideoFrameWasDisplayed() {
        listener.onMediaItemTransition(event(first), first, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        assertFalse(state.value.hasRenderedVideoFrame)
        listener.onRenderedFirstFrame(event(first), Any(), 1L)
        assertTrue(state.value.hasRenderedVideoFrame)
    }

    @Test fun aQueuedFrameFromThePreviousChannelCannotCompleteItsReplacement() {
        listener.onMediaItemTransition(event(first), first, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        listener.onRenderedFirstFrame(event(first), Any(), 1L)
        listener.onMediaItemTransition(event(second), second, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        assertFalse(state.value.hasRenderedVideoFrame)
        listener.onRenderedFirstFrame(event(first), Any(), 2L)
        assertFalse(state.value.hasRenderedVideoFrame)
        listener.onRenderedFirstFrame(event(second), Any(), 3L)
        assertTrue(state.value.hasRenderedVideoFrame)
    }

    @Test fun callbacksAfterOwnerDestructionAreIgnored() {
        listener.onMediaItemTransition(event(first), first, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        listener.close()
        listener.onMediaItemTransition(event(second), second, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        listener.onRenderedFirstFrame(event(second), Any(), 1L)
        assertFalse(state.value.hasRenderedVideoFrame)
    }

    private fun event(item: MediaItem): AnalyticsListener.EventTime {
        val timeline = SinglePeriodTimeline(1_000_000L, true, false, false, null, item)
        return AnalyticsListener.EventTime(0L, timeline, 0, null, 0L, timeline, 0, null, 0L, 0L)
    }
}
