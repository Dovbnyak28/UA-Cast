package com.uacastplayer.player

import com.uacastplayer.player.PlayerContainerStateMachine.Event
import com.uacastplayer.player.PlayerContainerStateMachine.State
import com.uacastplayer.player.PlayerContainerStateMachine.reduce
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerContainerStateMachineTest {

    @Test
    fun `Open always goes to Expanded, from any state`() {
        assertEquals(State.EXPANDED, reduce(State.CLOSED, Event.Open))
        assertEquals(State.EXPANDED, reduce(State.COLLAPSED, Event.Open))
        assertEquals(State.EXPANDED, reduce(State.EXPANDED, Event.Open))
    }

    @Test
    fun `Close always goes to Closed, from any state`() {
        assertEquals(State.CLOSED, reduce(State.EXPANDED, Event.Close))
        assertEquals(State.CLOSED, reduce(State.COLLAPSED, Event.Close))
        assertEquals(State.CLOSED, reduce(State.CLOSED, Event.Close))
    }

    @Test
    fun `Tap expands only from Collapsed`() {
        assertEquals(State.EXPANDED, reduce(State.COLLAPSED, Event.Tap))
    }

    @Test
    fun `Tap is a no-op from Expanded or Closed`() {
        assertEquals(State.EXPANDED, reduce(State.EXPANDED, Event.Tap))
        assertEquals(State.CLOSED, reduce(State.CLOSED, Event.Tap))
    }

    @Test
    fun `Back collapses from Expanded`() {
        assertEquals(State.COLLAPSED, reduce(State.EXPANDED, Event.Back))
    }

    @Test
    fun `Back closes from Collapsed`() {
        assertEquals(State.CLOSED, reduce(State.COLLAPSED, Event.Back))
    }

    @Test
    fun `Back is a no-op from Closed`() {
        assertEquals(State.CLOSED, reduce(State.CLOSED, Event.Back))
    }

    @Test
    fun `a full collapse-then-reopen-then-close cycle`() {
        var state = State.CLOSED
        state = reduce(state, Event.Open)
        assertEquals(State.EXPANDED, state)
        state = reduce(state, Event.Back)
        assertEquals(State.COLLAPSED, state)
        state = reduce(state, Event.Tap)
        assertEquals(State.EXPANDED, state)
        state = reduce(state, Event.Back)
        assertEquals(State.COLLAPSED, state)
        state = reduce(state, Event.Back)
        assertEquals(State.CLOSED, state)
    }
}
