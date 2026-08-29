package com.uacastplayer.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

data class TabBarItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** Registers this tab as a guided-tour target under the given name; null for tabs the tour
     * never points at, which is most of them. See [com.uacastplayer.guidedtour.GuidedTourKeys]. */
    val tourKey: String? = null,
    /** Full destination name announced by accessibility services when the visible label must be
     * shortened to fit a compact phone navigation bar. */
    val contentDescription: String = label,
)
