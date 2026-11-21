package com.uacastplayer.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRootLayoutTest {

    @Test
    fun `compact widths use bottom bar`() {
        assertEquals(RootNavigationMode.BOTTOM_BAR, AdaptiveRootLayout.navigationModeFor(320))
        assertEquals(RootNavigationMode.BOTTOM_BAR, AdaptiveRootLayout.navigationModeFor(599))
    }

    @Test
    fun `medium and expanded widths use navigation rail`() {
        assertEquals(RootNavigationMode.NAVIGATION_RAIL, AdaptiveRootLayout.navigationModeFor(600))
        assertEquals(RootNavigationMode.NAVIGATION_RAIL, AdaptiveRootLayout.navigationModeFor(840))
    }

    @Test
    fun `expanded starts at 840dp`() {
        assertFalse(AdaptiveRootLayout.isExpanded(839))
        assertTrue(AdaptiveRootLayout.isExpanded(840))
    }
}
