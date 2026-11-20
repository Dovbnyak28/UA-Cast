package com.uacastplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniPlayerSubtitleTest {
    @Test fun remotePlaybackKeepsTheProgrammeAsWellAsTheReceiverBadge() {
        assertEquals("On TV · Evening news", miniPlayerSubtitle("On TV", "Evening news"))
    }

    @Test fun localPlaybackKeepsTheProgrammeWithoutInventingAReceiver() {
        assertEquals("Evening news", miniPlayerSubtitle(null, "Evening news"))
        assertEquals("On TV", miniPlayerSubtitle("On TV", null))
    }

    @Test fun missingGuideAndReceiverDoesNotLeaveAnEmptyCaption() {
        assertNull(miniPlayerSubtitle(null, null))
    }
}
