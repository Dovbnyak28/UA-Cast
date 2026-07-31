package com.uacastplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerGesturePolicyTest {

    @Test
    fun `zoneFor classifies left third`() {
        assertEquals(PlayerGesturePolicy.GestureZone.LEFT, PlayerGesturePolicy.zoneFor(0f))
        assertEquals(PlayerGesturePolicy.GestureZone.LEFT, PlayerGesturePolicy.zoneFor(0.32f))
    }

    @Test
    fun `zoneFor classifies center third`() {
        assertEquals(PlayerGesturePolicy.GestureZone.CENTER, PlayerGesturePolicy.zoneFor(0.34f))
        assertEquals(PlayerGesturePolicy.GestureZone.CENTER, PlayerGesturePolicy.zoneFor(0.5f))
        assertEquals(PlayerGesturePolicy.GestureZone.CENTER, PlayerGesturePolicy.zoneFor(0.66f))
    }

    @Test
    fun `zoneFor classifies right third`() {
        assertEquals(PlayerGesturePolicy.GestureZone.RIGHT, PlayerGesturePolicy.zoneFor(0.68f))
        assertEquals(PlayerGesturePolicy.GestureZone.RIGHT, PlayerGesturePolicy.zoneFor(1f))
    }

    @Test
    fun `leftward swipe past threshold requests next channel`() {
        assertEquals(PlayerGesturePolicy.SwipeChannelAction.NEXT, PlayerGesturePolicy.channelSwipeAction(-0.2f))
    }

    @Test
    fun `rightward swipe past threshold requests previous channel`() {
        assertEquals(PlayerGesturePolicy.SwipeChannelAction.PREVIOUS, PlayerGesturePolicy.channelSwipeAction(0.2f))
    }

    @Test
    fun `swipe below threshold is ignored`() {
        assertNull(PlayerGesturePolicy.channelSwipeAction(0.1f))
        assertNull(PlayerGesturePolicy.channelSwipeAction(-0.1f))
        assertNull(PlayerGesturePolicy.channelSwipeAction(0f))
    }

    @Test
    fun `swipe exactly at threshold counts as intentional`() {
        assertEquals(
            PlayerGesturePolicy.SwipeChannelAction.NEXT,
            PlayerGesturePolicy.channelSwipeAction(-PlayerGesturePolicy.MIN_SWIPE_WIDTH_FRACTION),
        )
        assertEquals(
            PlayerGesturePolicy.SwipeChannelAction.PREVIOUS,
            PlayerGesturePolicy.channelSwipeAction(PlayerGesturePolicy.MIN_SWIPE_WIDTH_FRACTION),
        )
    }

    @Test
    fun `levelDelta inverts drag direction so dragging up increases level`() {
        assertEquals(0.2f, PlayerGesturePolicy.levelDelta(-0.2f), 0f)
        assertEquals(-0.2f, PlayerGesturePolicy.levelDelta(0.2f), 0f)
    }

    @Test
    fun `applyLevelDelta clamps to valid range`() {
        assertEquals(1f, PlayerGesturePolicy.applyLevelDelta(0.9f, 0.5f), 0f)
        assertEquals(0f, PlayerGesturePolicy.applyLevelDelta(0.1f, -0.5f), 0f)
        assertEquals(0.6f, PlayerGesturePolicy.applyLevelDelta(0.5f, 0.1f), 0.0001f)
    }
}
