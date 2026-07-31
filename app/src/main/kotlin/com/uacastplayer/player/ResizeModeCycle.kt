package com.uacastplayer.player

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
