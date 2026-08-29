package com.uacastplayer.player

import androidx.media3.common.Player
import androidx.media3.session.SessionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionPlayerCommandDispatcherTest {

    @Test
    fun `next dispatches once and reports success`() {
        var nextCalls = 0
        var previousCalls = 0

        val result = MediaSessionPlayerCommandDispatcher.dispatch(
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            onNext = { nextCalls++ },
            onPrevious = { previousCalls++ },
        )

        assertEquals(SessionResult.RESULT_SUCCESS, result)
        assertEquals(1, nextCalls)
        assertEquals(0, previousCalls)
    }

    @Test
    fun `previous dispatches once and reports success`() {
        var nextCalls = 0
        var previousCalls = 0

        val result = MediaSessionPlayerCommandDispatcher.dispatch(
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            onNext = { nextCalls++ },
            onPrevious = { previousCalls++ },
        )

        assertEquals(SessionResult.RESULT_SUCCESS, result)
        assertEquals(0, nextCalls)
        assertEquals(1, previousCalls)
    }

    @Test
    fun `an unrelated command delegates without side effects`() {
        var calls = 0

        val result = MediaSessionPlayerCommandDispatcher.dispatch(
            Player.COMMAND_PLAY_PAUSE,
            onNext = { calls++ },
            onPrevious = { calls++ },
        )

        assertNull(result)
        assertEquals(0, calls)
    }
}
