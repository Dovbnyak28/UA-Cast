package app.zorik.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zorik.player.ui.favorites.FavoriteUndoSection
import app.zorik.player.ui.favorites.LocalFavoriteUndo

@Composable
internal fun FavoriteUndoProvider(viewModel: AppViewModel, content: @Composable () -> Unit) {
    val owner = viewModel.favoritesRepository.removalUndo
    val notice by owner.notice.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalFavoriteUndo provides FavoriteUndoSection(notice, owner::undo, owner::dismiss),
        content = content)
}
