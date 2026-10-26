package com.uacastplayer.performance

import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTierDefaultsTest {

    @Test
    fun `LOW_END defaults to placeholder icons and minimal density`() {
        assertEquals(IconDisplayMode.PLACEHOLDERS, DeviceTierDefaults.iconDisplayMode(DeviceTier.LOW_END))
        assertEquals(ListDensity.MINIMAL, DeviceTierDefaults.listDensity(DeviceTier.LOW_END))
    }

    @Test
    fun `MID_RANGE defaults to cache-limited icons and simple density`() {
        assertEquals(IconDisplayMode.CACHE_LIMITED, DeviceTierDefaults.iconDisplayMode(DeviceTier.MID_RANGE))
        assertEquals(ListDensity.SIMPLE, DeviceTierDefaults.listDensity(DeviceTier.MID_RANGE))
    }

    @Test
    fun `HIGH_END defaults to full caching and full density`() {
        assertEquals(IconDisplayMode.CACHE, DeviceTierDefaults.iconDisplayMode(DeviceTier.HIGH_END))
        assertEquals(ListDensity.FULL, DeviceTierDefaults.listDensity(DeviceTier.HIGH_END))
    }
}
