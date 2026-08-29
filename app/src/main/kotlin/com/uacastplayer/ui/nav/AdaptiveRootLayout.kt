package com.uacastplayer.ui.nav

internal enum class RootNavigationMode { BOTTOM_BAR, NAVIGATION_RAIL }

internal object AdaptiveRootLayout {
    const val MEDIUM_WIDTH_DP = 600
    const val EXPANDED_WIDTH_DP = 840

    fun navigationModeFor(widthDp: Int): RootNavigationMode =
        if (widthDp >= MEDIUM_WIDTH_DP) RootNavigationMode.NAVIGATION_RAIL else RootNavigationMode.BOTTOM_BAR

    fun isExpanded(widthDp: Int): Boolean = widthDp >= EXPANDED_WIDTH_DP
}
