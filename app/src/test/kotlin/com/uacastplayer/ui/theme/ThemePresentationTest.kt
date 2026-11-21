package com.uacastplayer.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresentationTest {
    @Test fun televisionNeverOffersAPhoneOnlyThemeThatItCannotDisplay() {
        assertFalse(AppTheme.DAYLIGHT in availableAppThemes(true))
        assertFalse(AppTheme.SYSTEM in availableAppThemes(true))
        availableAppThemes(true).forEach { assertEquals(it, themeForScreen(it, true)) }
    }

    @Test fun phoneSupportsAllThemesAndRestoredTvDaylightHasOneConsistentFallback() {
        assertTrue(AppTheme.DAYLIGHT in availableAppThemes(false))
        assertEquals(AppTheme.DAYLIGHT, themeForScreen(AppTheme.DAYLIGHT, false))
        assertEquals(AppTheme.MIDNIGHT, themeForScreen(AppTheme.DAYLIGHT, true))
    }
    @Test fun systemPreferenceResolvesAtRenderTimeAndExplicitStylesStayExplicit() {
        assertEquals(AppTheme.DAYLIGHT, resolvedAppTheme(AppTheme.SYSTEM, false))
        assertEquals(AppTheme.MIDNIGHT, resolvedAppTheme(AppTheme.SYSTEM, true))
        assertEquals(AppTheme.SYSTEM, themeForScreen(AppTheme.SYSTEM, false))
        assertEquals(AppTheme.MIDNIGHT, themeForScreen(AppTheme.SYSTEM, true))
        assertEquals(AppTheme.CINEMA, resolvedAppTheme(AppTheme.CINEMA, false))
    }
}
