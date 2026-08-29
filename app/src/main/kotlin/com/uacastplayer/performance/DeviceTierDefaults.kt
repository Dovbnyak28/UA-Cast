package com.uacastplayer.performance

import com.uacastplayer.core.settings.IconDisplayMode
import com.uacastplayer.core.settings.ListDensity

/** Computed defaults for a device tier. Explicit user choices always win. */
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
