package app.zorik.player.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBackStackReducerTest {
    @Test fun `initial state starts at home`() {
        assertEquals(BottomDestination.HOME, BottomNavState().current)
    }

    @Test fun `selecting the current tab is a no-op`() {
        val state = BottomNavState(listOf(BottomDestination.HOME))
        val result = NavBackStackReducer.reduce(state, BottomNavEvent.Select(BottomDestination.HOME))
        assertEquals(state, result.state)
        assertFalse(result.shouldExitApp)
    }

    @Test fun `tabs replace each other instead of accumulating history`() {
        var state = BottomNavState()
        repeat(30) { index ->
            val destination = BottomDestination.entries[index % BottomDestination.entries.size]
            state = NavBackStackReducer.reduce(state, BottomNavEvent.Select(destination)).state
            assertEquals(destination, state.current)
            assertEquals(BottomDestination.HOME, state.stack.first())
            assertTrue(state.stack.size <= 2)
        }
    }

    @Test fun `selecting home discards prior tab visits`() {
        val state = BottomNavState(listOf(BottomDestination.HOME, BottomDestination.CHANNELS,
            BottomDestination.FAVORITES, BottomDestination.SETTINGS))
        val result = NavBackStackReducer.reduce(state, BottomNavEvent.Select(BottomDestination.HOME))
        assertEquals(BottomNavState(), result.state)
    }

    @Test fun `back from any sibling tab returns directly home`() {
        BottomDestination.entries.filter { it != BottomDestination.HOME }.forEach { destination ->
            val state = BottomNavState(listOf(BottomDestination.HOME, BottomDestination.CHANNELS, destination))
            val result = NavBackStackReducer.reduce(state, BottomNavEvent.Back)
            assertEquals(BottomNavState(), result.state)
            assertFalse(result.shouldExitApp)
        }
    }

    @Test fun `restored single non-home tab cannot exit the app on first back`() {
        val result = NavBackStackReducer.reduce(BottomNavState(listOf(BottomDestination.SETTINGS)),
            BottomNavEvent.Back)
        assertFalse(result.shouldExitApp)
        assertEquals(BottomNavState(), result.state)
    }

    @Test fun `back on home exits even if an older version saved tab history`() {
        val state = BottomNavState(listOf(BottomDestination.CHANNELS, BottomDestination.HOME))
        assertTrue(NavBackStackReducer.reduce(state, BottomNavEvent.Back).shouldExitApp)
    }
}
