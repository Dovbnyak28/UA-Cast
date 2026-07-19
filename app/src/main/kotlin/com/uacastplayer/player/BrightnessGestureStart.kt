package com.uacastplayer.player

/**
 * Decides what brightness level the fullscreen player's brightness gesture should start from,
 * independent of how the window/system brightness is actually read. Kept separate from
 * `PlayerScreen` so the logic is testable without a Compose UI test.
 */
object BrightnessGestureStart {

    private const val FALLBACK_LEVEL = 0.5f

    /**
     * [windowOverride] is `window.attributes.screenBrightness` - a negative value means the
     * window isn't overriding brightness yet (following the system/auto setting), so in that
     * case the gesture should start from the actual current screen brightness instead of
     * silently jumping from whatever the window's un-set default happens to render as.
     * [systemBrightness] is the device's system brightness as a 0f..1f fraction, or null if it
     * couldn't be read (e.g. `Settings.System.SCREEN_BRIGHTNESS` threw).
     */
    fun level(windowOverride: Float, systemBrightness: Float?): Float = when {
        windowOverride >= 0f -> windowOverride
        systemBrightness != null -> systemBrightness
        else -> FALLBACK_LEVEL
    }
}
