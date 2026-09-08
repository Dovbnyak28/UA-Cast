package com.uacastplayer.core.nav

/** Ordered history of visited bottom-nav tabs; the last entry is the currently selected tab. */
data class BottomNavState(val stack: List<BottomDestination> = listOf(BottomDestination.START)) {
    val current: BottomDestination get() = stack.last()
}

sealed interface BottomNavEvent {
    data class Select(val destination: BottomDestination) : BottomNavEvent
    data object Back : BottomNavEvent
}

data class BottomNavResult(val state: BottomNavState, val shouldExitApp: Boolean)

/**
 * Pure reducer for bottom-navigation tab selection and system back handling. Tracks tab visit
 * order so back navigates to the previously selected tab instead of a fixed hierarchy, and
 * requests app exit once the history is exhausted.
 */
object NavBackStackReducer {

    fun reduce(state: BottomNavState, event: BottomNavEvent): BottomNavResult = when (event) {
        is BottomNavEvent.Select -> select(state, event.destination)
        BottomNavEvent.Back -> back(state)
    }

    private fun select(state: BottomNavState, destination: BottomDestination): BottomNavResult {
        if (destination == state.current) return BottomNavResult(state, shouldExitApp = false)
        val reordered = state.stack.filterNot { it == destination } + destination
        return BottomNavResult(state.copy(stack = reordered), shouldExitApp = false)
    }

    private fun back(state: BottomNavState): BottomNavResult {
        if (state.stack.size <= 1) return BottomNavResult(state, shouldExitApp = true)
        return BottomNavResult(state.copy(stack = state.stack.dropLast(1)), shouldExitApp = false)
    }
}
