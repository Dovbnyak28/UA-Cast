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

    private fun keyOf(channel: M3uChannel) = channel.displayName

    private val lockedKeys = setOf(locked.displayName, alsoLocked.displayName)

    private fun selectionFrom(startIndex: Int, sessionUnlocked: Boolean) =
        PlayerChannelAccess.forSession(all, startIndex, lockedKeys, ::keyOf, sessionUnlocked)

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
        val selection = PlayerChannelAccess.forSession(all, 9, lockedKeys, ::keyOf, sessionUnlocked = false)

        assertSame(all, selection.channels)
        assertEquals(9, selection.startIndex)
    }

    /**
     * The other door, and the one no tap ever passes through: after the system reclaims the
     * process, `unlockedThisSession` is gone by design and the saved player request reopened
     * whatever was playing. Parent enters the PIN, watches a locked channel, the process dies -
     * and the next person to open the app is watching it.
     */
    @Test
    fun `a locked channel does not come back by itself after process death`() {
        assertEquals(
            false,
            PlayerChannelAccess.mayRestoreAfterProcessDeath(locked, lockedKeys, ::keyOf, sessionUnlocked = false),
        )
    }

    @Test
    fun `an unlocked channel still comes back after process death`() {
        assertEquals(
            true,
            PlayerChannelAccess.mayRestoreAfterProcessDeath(one, lockedKeys, ::keyOf, sessionUnlocked = false),
        )
    }

    /** Only reachable when the PIN was entered *after* the restore, but the rule is the same one
     * [PlayerChannelAccess.forSession] follows and they must not disagree. */
    @Test
    fun `an unlocked session restores a locked channel like any other`() {
        assertEquals(
            true,
            PlayerChannelAccess.mayRestoreAfterProcessDeath(locked, lockedKeys, ::keyOf, sessionUnlocked = true),
        )
    }

    /**
     * The cost guarantee, and the reason [lockedKeys] is a set here rather than a predicate.
     *
     * Keying a channel is a SHA-256 for anything without a `tvg-id`, and this runs over the whole
     * playlist the moment the player opens - 40ms per 40,000 such channels on a desktop JVM. Nobody
     * who has never locked a channel should pay any of it, so the empty case must be answered
     * before a single key is computed. Counted, not assumed.
     */
    @Test
    fun `nothing is keyed when no channel is locked`() {
        var keysComputed = 0
        val counting: (M3uChannel) -> String = { channel -> keysComputed++; keyOf(channel) }

        val selection = PlayerChannelAccess.forSession(all, 0, emptySet(), counting, sessionUnlocked = false)

        assertEquals(0, keysComputed)
        assertSame("and the list is handed straight back, not copied", all, selection.channels)
        assertEquals(0, selection.startIndex)
    }

    /** Same guarantee on the restore path, which runs on every cold start with a saved channel. */
    @Test
    fun `nothing is keyed on restore when no channel is locked`() {
        var keysComputed = 0
        val counting: (M3uChannel) -> String = { channel -> keysComputed++; keyOf(channel) }

        val mayRestore = PlayerChannelAccess.mayRestoreAfterProcessDeath(one, emptySet(), counting, false)

        assertEquals(true, mayRestore)
        assertEquals(0, keysComputed)
    }

    /** The started channel is kept without being keyed either - it is kept whatever it is. */
    @Test
    fun `the started channel costs no key`() {
        var keyed = mutableListOf<String>()
        val recording: (M3uChannel) -> String = { channel -> keyOf(channel).also { keyed += it } }

        PlayerChannelAccess.forSession(all, 0, lockedKeys, recording, sessionUnlocked = false)

        assertEquals(listOf("Locked", "Two", "AlsoLocked", "Three"), keyed)
    }
}
