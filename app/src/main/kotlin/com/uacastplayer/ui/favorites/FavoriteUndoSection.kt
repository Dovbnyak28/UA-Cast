package com.uacastplayer.ui.favorites

import androidx.compose.runtime.staticCompositionLocalOf
import com.uacastplayer.favorites.FavoriteRemovalNotice

data class FavoriteUndoSection(
    val notice: FavoriteRemovalNotice? = null,
    val onUndo: (Long) -> Unit = {},
    val onDismiss: (Long) -> Unit = {},
)

val LocalFavoriteUndo = staticCompositionLocalOf { FavoriteUndoSection() }

