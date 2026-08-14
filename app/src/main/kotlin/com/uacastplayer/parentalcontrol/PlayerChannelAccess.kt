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
 * The started channel is always kept. In the path that matters it is unlocked anyway; keeping it
 * unconditionally is what makes this total, so a caller cannot land the player on an index that
 * points at nothing.
 */
object PlayerChannelAccess {

    data class Selection(val channels: List<M3uChannel>, val startIndex: Int)

    fun forSession(
        channels: List<M3uChannel>,
        startIndex: Int,
        isLocked: (M3uChannel) -> Boolean,
        sessionUnlocked: Boolean,
    ): Selection {
        // Two ways there is nothing to narrow, kept in one condition so this reads as a single
        // early exit. Unlocked: a correct PIN opens every locked channel for the rest of the
        // session, which is the feature's stated design ("until the app is closed"). Out of range:
        // the caller's business, not this policy's - answering it would quietly change which
        // channel opens.
        if (sessionUnlocked || startIndex !in channels.indices) return Selection(channels, startIndex)

        val kept = ArrayList<M3uChannel>(channels.size)
        var keptStartIndex = 0
        channels.forEachIndexed { index, channel ->
            if (index == startIndex) {
                keptStartIndex = kept.size
                kept += channel
            } else if (!isLocked(channel)) {
                kept += channel
            }
        }
        return Selection(kept, keptStartIndex)
    }
}
