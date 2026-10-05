package com.uacastplayer.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test fun `wifi spelling and case variants match one control`() {
        val entry = SettingsSearchEntry(0, "Логотипи лише через Wi-Fi", SettingsPage.PLAYBACK)
        listOf("wifi", "WI-FI", "wi fi", "логотипи wifi").forEach { assertTrue(entry.matches(it)) }
        assertFalse(entry.matches("wifi відео"))
        assertFalse(entry.matches("   "))
    }

    @Test fun `technical aliases find localized EPG source`() {
        val entry = SettingsSearchEntry(0, "Джерело телепрограми", SettingsPage.GENERAL, "EPG XMLTV")
        assertTrue(entry.matches("xmltv"))
        assertTrue(entry.matches("джерело EPG"))
        assertFalse(entry.matches("PIN"))
    }
}
