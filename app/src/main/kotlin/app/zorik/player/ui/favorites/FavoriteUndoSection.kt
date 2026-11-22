package app.zorik.player.ui.favorites

import androidx.compose.runtime.staticCompositionLocalOf
import app.zorik.player.favorites.FavoriteRemovalNotice

data class FavoriteUndoSection(
    val notice: FavoriteRemovalNotice? = null,
    val onUndo: (Long) -> Unit = {},
    val onDismiss: (Long) -> Unit = {},
)

val LocalFavoriteUndo = staticCompositionLocalOf { FavoriteUndoSection() }

