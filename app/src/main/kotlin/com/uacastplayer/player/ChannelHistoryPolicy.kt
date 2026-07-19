package com.uacastplayer.player

/**
 * Tracks exactly one step of "previous channel" history across switches - like a TV remote's
 * last-channel button, not the full navigation stack. Switching to the channel that's *already*
 * current is a no-op (e.g. a debounced re-switch firing for the same index the user already
 * landed on), so it never clobbers what was actually being watched before.
 */
object ChannelHistoryPolicy {

    /** [current]/[previous] are `null` before any channel has ever loaded. */
    data class State(val current: Int?, val previous: Int?)

    fun onSwitch(state: State, newIndex: Int): State =
        if (newIndex == state.current) state else State(current = newIndex, previous = state.current)
}
