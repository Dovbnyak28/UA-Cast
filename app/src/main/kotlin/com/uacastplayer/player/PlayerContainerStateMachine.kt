package com.uacastplayer.player

/**
 * Governs whether the player is shown fullscreen, as a small persistent bar above the bottom
 * navigation, or not at all - independent of whether a channel is actually loaded (that's
 * [com.uacastplayer.player.PlayerViewModel]/the caller's own `playerRequest` nullability; this
 * machine only decides the *layout* once one is).
 */
object PlayerContainerStateMachine {

    enum class State { CLOSED, EXPANDED, COLLAPSED }

    sealed interface Event {
        /** A channel was picked (from a channel list, "continue watching", or restored after
         * process death) - always opens fullscreen, regardless of the previous state. */
        data object Open : Event

        /** The collapsed bar itself was tapped - expands to fullscreen. */
        data object Tap : Event

        /** The system back gesture/button - collapses from fullscreen, or closes from collapsed. */
        data object Back : Event

        /** The bar's own close (X) button, or exiting fullscreen via its own exit affordance -
         * always closes outright, regardless of the previous state. */
        data object Close : Event
    }

    fun reduce(state: State, event: Event): State = when (event) {
        Event.Open -> State.EXPANDED
        Event.Close -> State.CLOSED
        Event.Tap -> if (state == State.COLLAPSED) State.EXPANDED else state
        Event.Back -> when (state) {
            State.EXPANDED -> State.COLLAPSED
            State.COLLAPSED -> State.CLOSED
            State.CLOSED -> State.CLOSED
        }
    }
}
