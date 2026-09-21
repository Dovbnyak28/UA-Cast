package com.uacastplayer.ui.nav

import androidx.annotation.StringRes
import com.uacastplayer.R
import com.uacastplayer.core.nav.BottomDestination

/** Large text keeps meaningful words visible instead of splitting a tab name mid-word. */
@StringRes
internal fun BottomDestination.largeTextLabelRes(): Int = when (this) {
    BottomDestination.HOME -> R.string.nav_large_home
    BottomDestination.CHANNELS -> R.string.nav_large_channels
    BottomDestination.FAVORITES -> R.string.nav_large_favorites
    BottomDestination.SETTINGS -> R.string.nav_large_settings
}
