package app.zorik.player.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

data class TabBarItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** Full destination name announced by accessibility services when the visible label must be
     * shortened to fit a compact phone navigation bar. */
    val contentDescription: String = label,
    /** Short words at accessibility font sizes; the full name above remains the spoken label. */
    val largeTextLabel: String = label,
)
