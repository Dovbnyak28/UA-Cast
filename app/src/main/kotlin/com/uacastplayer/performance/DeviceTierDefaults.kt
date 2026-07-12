package com.uacastplayer.performance

import com.uacastplayer.data.prefs.IconDisplayMode
import com.uacastplayer.data.prefs.ListDensity

/** Computed defaults for a device tier - always overridable, and once the user picks a value explicitly it wins forever. */
object DeviceTierDefaults {

    fun iconDisplayMode(tier: DeviceTier): IconDisplayMode = when (tier) {
        DeviceTier.LOW_END -> IconDisplayMode.PLACEHOLDERS
        DeviceTier.MID_RANGE -> IconDisplayMode.CACHE_LIMITED
        DeviceTier.HIGH_END -> IconDisplayMode.CACHE
    }

    fun listDensity(tier: DeviceTier): ListDensity = when (tier) {
        DeviceTier.LOW_END -> ListDensity.MINIMAL
        DeviceTier.MID_RANGE -> ListDensity.SIMPLE
        DeviceTier.HIGH_END -> ListDensity.FULL
    }
}
