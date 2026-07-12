package com.uacastplayer.icons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchGateTest {

    @Test
    fun `never runs when offline`() {
        assertFalse(PrefetchGate.canPrefetchNow(wifiOnlyEnabled = true, isConnected = false, isMetered = false))
        assertFalse(PrefetchGate.canPrefetchNow(wifiOnlyEnabled = false, isConnected = false, isMetered = false))
    }

    @Test
    fun `wifi-only blocks a metered connection`() {
        assertFalse(PrefetchGate.canPrefetchNow(wifiOnlyEnabled = true, isConnected = true, isMetered = true))
    }

    @Test
    fun `wifi-only allows an unmetered connection`() {
        assertTrue(PrefetchGate.canPrefetchNow(wifiOnlyEnabled = true, isConnected = true, isMetered = false))
    }

    @Test
    fun `disabling wifi-only allows a metered connection`() {
        assertTrue(PrefetchGate.canPrefetchNow(wifiOnlyEnabled = false, isConnected = true, isMetered = true))
    }
}
