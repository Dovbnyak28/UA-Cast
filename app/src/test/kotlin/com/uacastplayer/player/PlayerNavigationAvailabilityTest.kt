package com.uacastplayer.player

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerNavigationAvailabilityTest {
    private val session = PlayerSessionStateMachine()
    private val channels = List(3) { M3uChannel("C$it", "https://example.test/$it") }

    @Test fun `unstarted and released sessions have no navigation`() {
        assertNull(session.nextIndex(true))
        session.start(channels, 0, false)
        session.release()
        assertNull(session.nextIndex(true))
        assertNull(session.previousIndex(true))
    }

    @Test fun `single channel is not its own next or previous even when wrapping`() {
        session.start(channels.take(1), 0, true)
        assertNull(session.nextIndex(true))
        assertNull(session.previousIndex(true))
    }

    @Test fun `boundaries and wrap setting immediately change availability`() {
        session.start(channels, 0, false)
        assertNull(session.previousIndex(false))
        assertEquals(2, session.previousIndex(true))
        assertEquals(1, session.nextIndex(false))
        session.switchTo(2, false)
        assertNull(session.nextIndex(false))
        assertEquals(0, session.nextIndex(true))
        assertEquals(1, session.previousIndex(false))
        assertEquals(emptyList<IndexedChannel>(), session.nextPreview(false))
        assertEquals(listOf(0, 1), session.nextPreview(true).map { it.index })
        session.release()
        assertEquals(emptyList<IndexedChannel>(), session.nextPreview(true))
    }
}
