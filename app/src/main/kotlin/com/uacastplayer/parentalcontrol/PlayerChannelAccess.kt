package com.uacastplayer.parentalcontrol

import com.uacastplayer.playlist.M3uChannel

/**
 * Which channels the player may be handed, given whether the PIN has been entered this session.
 *
 * [com.uacastplayer.app.ParentalControlController]'s own doc states the rule this enforces:
 * *watching* a locked channel needs `unlockedThisSession` first. That was checked in exactly one
 * place - the moment the player opens, against the one channel that was tapped - and the player
 * itself knows nothing about parental control at all (deliberately: it is a separate ViewModel with
 * no reference to this feature). So the check covered the tap and nothing after it: open any
 * unlocked channel, press next, and the locked one three positions along plays with no PIN asked.
 * Its name was already visible before that, in the player's own next-channels preview.
 *
 * Answered by narrowing what the player receives rather than by teaching it about locks, because
 * that is what the feature already says it does - "locking a channel only ever narrows what's
 * playable, nothing to protect by gating it". A locked channel is simply not in the rotation until
 * the PIN opens the session, at which point everything is handed over as before.
 *
 * **[lockedKeys] is taken as a set rather than hidden behind an `isLocked` predicate, and that is
 * about cost, not taste.** Keying a channel means [com.uacastplayer.favorites.FavoriteKey.of],
 * which is a SHA-256 for any channel without a `tvg-id` - and this runs over the whole playlist at
 * the moment the player opens. Measured over 40,000 such channels: **40ms on a desktop JVM**, so
 * something like a fifth of a second of frozen UI on a mid-range phone. An empty set is the answer
 * for everyone who does not use parental control at all, and taking it here is what lets that be
 * answered before a single key is computed. `MainActivity` already documents the same scan, done
 * off the main thread, on the restore path.
 *
 * The remaining cost falls only on someone who has actually locked a channel, and its worst case -
 * tens of thousands of channels with no `tvg-id` anywhere - is a playlist that has no working EPG
 * either, since tvg-id is what the guide matches on. A playlist that carries them costs 4ms.
 */
object PlayerChannelAccess {

    data class Selection(val channels: List<M3uChannel>, val startIndex: Int)

    fun forSession(
        channels: List<M3uChannel>,
        startIndex: Int,
        lockedKeys: Set<String>,
        keyOf: (M3uChannel) -> String,
        sessionUnlocked: Boolean,
    ): Selection {
        // Three ways there is nothing to narrow, kept in one condition so this reads as a single
        // early exit - and so none of them costs a key. Unlocked: a correct PIN opens every locked
        // channel for the rest of the session, which is the feature's stated design ("until the app
        // is closed"). Nothing locked: the ordinary case, and the one the cost note above is about.
        // Out of range: the caller's business, not this policy's - answering it would quietly
        // change which channel opens.
        val nothingToNarrow = sessionUnlocked || lockedKeys.isEmpty() || startIndex !in channels.indices
        if (nothingToNarrow) return Selection(channels, startIndex)

        val kept = ArrayList<M3uChannel>(channels.size)
        var keptStartIndex = 0
        channels.forEachIndexed { index, channel ->
            // The started channel is always kept. In the path that matters it is unlocked anyway;
            // keeping it unconditionally is what makes this total, so a caller cannot land the
            // player on an index that points at nothing - and it costs no key either.
            if (index == startIndex) {
                keptStartIndex = kept.size
                kept += channel
            } else if (keyOf(channel) !in lockedKeys) {
                kept += channel
            }
        }
        return Selection(kept, keptStartIndex)
    }

    /**
     * Whether the channel that was playing when the process died may come back by itself.
     *
     * [forSession] deliberately always keeps the channel the caller started on - a tap has already
     * been through the PIN gate by then. A restore after process death has been through nothing:
     * `unlockedThisSession` is in-memory only and a fresh process starts locked again, which the
     * feature states as its design ("until the app is closed"). So the one path that reopens the
     * player without a tap needs the stricter answer, or the sequence is simply: parent enters the
     * PIN, watches a locked channel, the system reclaims the process - and the next person to open
     * the app is watching it, with nothing asked.
     *
     * False means treat the saved marker exactly like a channel that has left the playlist: drop
     * it and leave the player closed. Nothing is lost that a tap cannot recover, and that tap goes
     * through the gate.
     */
    fun mayRestoreAfterProcessDeath(
        channel: M3uChannel,
        lockedKeys: Set<String>,
        keyOf: (M3uChannel) -> String,
        sessionUnlocked: Boolean,
    ): Boolean = sessionUnlocked || lockedKeys.isEmpty() || keyOf(channel) !in lockedKeys
}
