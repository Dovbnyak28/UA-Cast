package com.uacastplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.uacastplayer.R
import com.uacastplayer.data.prefs.PlayerResizeMode

/**
 * Fit -> Fill -> Zoom -> Fit. Also maps [PlayerResizeMode] to the two things every caller actually
 * needs from it - the Media3 `AspectRatioFrameLayout` int constant for [PlayerView.setResizeMode]
 * and a label string resource for the transient on-screen indicator - so that mapping only lives
 * in one place.
 */
object ResizeModeCycle {

    fun next(current: PlayerResizeMode): PlayerResizeMode = when (current) {
        PlayerResizeMode.FIT -> PlayerResizeMode.FILL
        PlayerResizeMode.FILL -> PlayerResizeMode.ZOOM
        PlayerResizeMode.ZOOM -> PlayerResizeMode.FIT
    }

    // AspectRatioFrameLayout's constants are @UnstableApi, which is androidx.annotation.
    // experimental's flavour of opt-in, NOT Kotlin's @RequiresOptIn - so kotlin.OptIn does not
    // silence it. androidx.annotation.OptIn is the one that applies.
    @OptIn(UnstableApi::class)
    fun toMedia3ResizeMode(mode: PlayerResizeMode): Int = when (mode) {
        PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        PlayerResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    fun labelRes(mode: PlayerResizeMode): Int = when (mode) {
        PlayerResizeMode.FIT -> R.string.player_resize_fit
        PlayerResizeMode.FILL -> R.string.player_resize_fill
        PlayerResizeMode.ZOOM -> R.string.player_resize_zoom
    }
}
