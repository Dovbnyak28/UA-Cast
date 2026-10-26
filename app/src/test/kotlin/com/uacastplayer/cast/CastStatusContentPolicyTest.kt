package com.uacastplayer.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastStatusContentPolicyTest {
    @Test
    fun acceptsMatchingContentId() {
        assertTrue(
            CastStatusContentPolicy.shouldAccept(
                "https://example.test/a.m3u8",
                "https://example.test/a.m3u8",
            ),
        )
    }

    @Test
    fun rejectsStatusForPreviousChannel() {
        assertFalse(
            CastStatusContentPolicy.shouldAccept(
                "https://example.test/old.m3u8",
                "https://example.test/new.m3u8",
            ),
        )
    }

    @Test
    fun acceptsUnknownIdsBecauseSomeReceiverStatesHaveNoMediaInfo() {
        assertTrue(CastStatusContentPolicy.shouldAccept(null, "https://example.test/new.m3u8"))
        assertTrue(CastStatusContentPolicy.shouldAccept("", "https://example.test/new.m3u8"))
        assertTrue(CastStatusContentPolicy.shouldAccept("https://example.test/new.m3u8", null))
    }
}
