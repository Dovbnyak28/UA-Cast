package com.uacastplayer.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBackStackReducerTest {

    @Test
    fun `initial state starts at home`() {
        val state = BottomNavState()
        assertEquals(BottomDestination.HOME, state.current)
    }

    @Test
    fun `selecting the current tab is a no-op`() {
        val state = BottomNavState(listOf(BottomDestination.HOME))
        val result = NavBackStackReducer.reduce(state, BottomNavEvent.Select(BottomDestination.HOME))
        assertEquals(state, result.state)
        assertFalse(result.shouldExitApp)
    }

    @Test
    fun `selecting a new tab pushes it on top`() {
        val state = BottomNavState(listOf(BottomDestination.HOME))
        val result = NavBackStackReducer.reduce(state, BottomNavEvent.Select(BottomDestination.CHANNELS))
        assertEquals(listOf(BottomDestination.HOME, BottomDestination.CHANNELS), result.state.stack)
        assertEquals(BottomDestination.CHANNELS, result.state.current)
    }

    @Test
    fun `reselecting a previously visited tab moves it to top without duplicating`() {
        val state = BottomNavState(
            listOf(BottomDestination.HOME, BottomDestination.CHANNELS, BottomDestination.FAVORITES)
        )
        val result = NavBackStackReducer.reduce(state, BottomNavEvent.Select(BottomDestination.HOME))
        assertEquals(
            listOf(BottomDestination.CHANNELS, BottomDestination.FAVORITES, BottomDestination.HOME),
            result.state.stack
        )
    }

    @Test
    fun `back pops to the previously selected tab`() {
        val state = BottomNavState(listOf(BottomDestination.HOME, BottomDestination.CHANNELS))
        val result = NavBackStackReducer.reduce(state, BottomNavEvent.Back)
        assertEquals(listOf(BottomDestination.HOME), result.state.stack)
        assertFalse(result.shouldExitApp)
    }

    @Test
    fun `back with only one tab in history requests app exit`() {
        val state = BottomNavState(listOf(BottomDestination.SETTINGS))
        val result = NavBackStackReducer.reduce(state, BottomNavEvent.Back)
        assertTrue(result.shouldExitApp)
        assertEquals(state, result.state)
    }

    @Test
    fun `back does not request exit while history has multiple entries`() {
        val state = BottomNavState(
            listOf(BottomDestination.HOME, BottomDestination.CHANNELS, BottomDestination.SETTINGS)
        )
        val result = NavBackStackReducer.reduce(state, BottomNavEvent.Back)
        assertFalse(result.shouldExitApp)
        assertEquals(BottomDestination.CHANNELS, result.state.current)
    }
}
