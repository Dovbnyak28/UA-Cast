package app.zorik.player.core.nav

/** Root destination followed by the selected tab; retained as a list for saved-state compatibility. */
data class BottomNavState(val stack: List<BottomDestination> = listOf(BottomDestination.START)) {
    val current: BottomDestination get() = stack.last()
}

sealed interface BottomNavEvent {
    data class Select(val destination: BottomDestination) : BottomNavEvent
    data object Back : BottomNavEvent
}

data class BottomNavResult(val state: BottomNavState, val shouldExitApp: Boolean)

/**
 * Top-level tabs are siblings. Back returns to Home rather than replaying tab visits.
 * Child screens and the expanded player consume Back before it reaches this reducer.
 */
object NavBackStackReducer {

    fun reduce(state: BottomNavState, event: BottomNavEvent): BottomNavResult = when (event) {
        is BottomNavEvent.Select -> select(state, event.destination)
        BottomNavEvent.Back -> back(state)
    }

    private fun select(state: BottomNavState, destination: BottomDestination): BottomNavResult {
        val path = if (destination == BottomDestination.HOME) listOf(destination)
            else listOf(BottomDestination.HOME, destination)
        return BottomNavResult(state.copy(stack = path), shouldExitApp = false)
    }

    private fun back(state: BottomNavState): BottomNavResult {
        return if (state.current == BottomDestination.HOME) BottomNavResult(state, shouldExitApp = true)
            else select(state, BottomDestination.HOME)
    }
}
