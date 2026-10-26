package com.uacastplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FilePlaybackTimelineTest {
    @Test fun clocksAreReadableForMinutesHoursAndUnknownValues() {
        assertEquals("0:00", filePlaybackTime(-1))
        assertEquals("0:00", filePlaybackTime(0))
        assertEquals("1:05", filePlaybackTime(65_000))
        assertEquals("1:01:01", filePlaybackTime(3_661_000))
    }

    @Test fun longDurationsAndSeekBoundsDoNotOverflow() {
        val timeline = FilePlaybackTimeline(2_000, 5L * 60 * 60 * 1000)
        assertEquals(0L, timeline.positionAt(-1f))
        assertEquals(timeline.duration, timeline.positionAt(2f))
        assertEquals(timeline.position, timeline.positionAt(Float.NaN))
        assertEquals(timeline.duration / 2, timeline.positionAt(0.5f))
    }

    @Test fun missingDurationAndPositionsOutsideTheFileFailClosed() {
        assertEquals(0f, FilePlaybackTimeline(1000, -1).fraction)
        assertEquals(0L, FilePlaybackTimeline(1000, -1).positionAt(1f))
        assertEquals(1f, FilePlaybackTimeline(90_000, 60_000).fraction)
        assertEquals(0f, FilePlaybackTimeline(-100, 60_000).fraction)
    }
}
