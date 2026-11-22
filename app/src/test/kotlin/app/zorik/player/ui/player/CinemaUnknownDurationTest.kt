package app.zorik.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CinemaUnknownDurationTest {
    @Test fun unknownDurationPreservesElapsedTimeWithoutInventingASeekRange() {
        val timeline = FilePlaybackTimeline(5_000, androidx.media3.common.C.TIME_UNSET)
        assertEquals(5_000L, timeline.position)
        assertEquals(0L, timeline.duration)
        assertEquals(0f, timeline.fraction, 0f)
    }
}
