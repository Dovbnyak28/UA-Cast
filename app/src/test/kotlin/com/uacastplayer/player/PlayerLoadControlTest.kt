package com.uacastplayer.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import com.uacastplayer.core.settings.BufferSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlayerLoadControlTest {
    @Test fun `every profile stops allocating at byte target even below minimum duration`() {
        BufferSize.entries.forEach { size ->
            val profile = PlayerBufferProfiles.forSize(size)
            val control = PlayerEngineFactory.buildLoadControl(profile)
            val playerId = PlayerId("budget-test")
            control.onPrepared(playerId)
            val timeline = SinglePeriodTimeline(
                C.TIME_UNSET, false, true, true, null, MediaItem.fromUri("https://x/live.ts"),
            )
            val parameters = LoadControl.Parameters(
                playerId, timeline, MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)),
                0, 1_000_000, 1f, true, false, C.TIME_UNSET, C.TIME_UNSET,
            )
            assertTrue(control.shouldContinueLoading(parameters))
            val allocator = control.getAllocator(playerId)
            val allocations = buildList {
                while (allocator.totalBytesAllocated < profile.targetBufferBytes) add(allocator.allocate())
            }
            assertFalse("$size exceeded its memory budget", control.shouldContinueLoading(parameters))
            allocations.forEach(allocator::release)
            control.onReleased(playerId)
        }
    }
}
