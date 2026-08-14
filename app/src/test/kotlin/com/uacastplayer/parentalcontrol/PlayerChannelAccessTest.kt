package com.uacastplayer.parentalcontrol

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The bypass this closes, stated as a sequence: lock a channel, never enter the PIN, open any
 * *unlocked* channel, and press next until the locked one comes round. It played, with nothing
 * asked - and its name was visible before that, in the player's next-channels preview.
 *
 * The lock was checked in exactly one place: the moment the player opens, against the one channel
 * that was tapped. Nothing after that tap consulted it, because the player deliberately knows
 * nothing about parental control - and [com.uacastplayer.app.ParentalControlController]'s own doc
 * names *watching a locked channel* as one of the things that must need the PIN first.
 *
 * These tests cover the policy. The one line that wires it into `MainActivity.openPlayerReal` is
 * not covered by a test - that is Activity-level composition, and this project's only harness for
 * it is the instrumented suite, which needs a device.
 */
class PlayerChannelAccessTest {

    private fun channel(name: String) = M3uChannel(displayName = name, streamUrl = "http://example.test/$name.ts")

    private val one = channel("One")
    private val locked = channel("Locked")
    private val two = channel("Two")
    private val alsoLocked = channel("AlsoLocked")
    private val three = channel("Three")

    private val all = listOf(one, locked, two, alsoLocked, three)

    private fun isLocked(channel: M3uChannel) = channel === locked || channel === alsoLocked

    private fun selectionFrom(startIndex: Int, sessionUnlocked: Boolean) =
        PlayerChannelAccess.forSession(all, startIndex, ::isLocked, sessionUnlocked)

    /** The bypass itself: opening an unlocked channel must not put locked ones within reach of the
     * next button. */
    @Test
    fun `a locked session hands the player no locked channels`() {
        val selection = selectionFrom(startIndex = 0, sessionUnlocked = false)

        assertEquals(listOf(one, two, three), selection.channels)
    }

    /** Removing entries moves everything after them, so the started channel has to be re-found -
     * getting this wrong would open a different channel than the one tapped. */
    @Test
    fun `the started channel is still the one that opens after the locked ones are dropped`() {
        val selection = selectionFrom(startIndex = 4, sessionUnlocked = false)

        assertEquals(listOf(one, two, three), selection.channels)
        assertEquals(2, selection.startIndex)
        assertSame(three, selection.channels[selection.startIndex])
    }

    @Test
    fun `a channel in the middle keeps its own position`() {
        val selection = selectionFrom(startIndex = 2, sessionUnlocked = false)

        assertEquals(1, selection.startIndex)
        assertSame(two, selection.channels[selection.startIndex])
    }

    /**
     * A correct PIN opens everything for the rest of the session - that is the feature's stated
     * design, and it is also what makes the gated "tapped a locked channel" path work: by the time
     * the player opens, the session is already unlocked.
     */
    @Test
    fun `an unlocked session hands the player everything, untouched`() {
        val selection = selectionFrom(startIndex = 1, sessionUnlocked = true)

        assertSame(all, selection.channels)
        assertEquals(1, selection.startIndex)
    }

    /**
     * Total by construction. The started channel is kept whatever it is, so no caller can land the
     * player on an index pointing at nothing - in the path that matters it is unlocked anyway.
     */
    @Test
    fun `the started channel is kept even when it is itself locked`() {
        val selection = selectionFrom(startIndex = 1, sessionUnlocked = false)

        assertEquals(listOf(one, locked, two, three), selection.channels)
        assertSame(locked, selection.channels[selection.startIndex])
    }

    @Test
    fun `an out of range start is left exactly as it came in`() {
        val selection = PlayerChannelAccess.forSession(all, startIndex = 9, ::isLocked, sessionUnlocked = false)

        assertSame(all, selection.channels)
        assertEquals(9, selection.startIndex)
    }

    @Test
    fun `a playlist with nothing locked is passed through unchanged`() {
        val selection = PlayerChannelAccess.forSession(all, startIndex = 0, { false }, sessionUnlocked = false)

        assertEquals(all, selection.channels)
        assertEquals(0, selection.startIndex)
    }
}
