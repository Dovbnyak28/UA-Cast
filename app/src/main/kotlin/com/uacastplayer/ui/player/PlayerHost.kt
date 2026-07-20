package com.uacastplayer.ui.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.playlist.M3uChannel
import java.io.File

/** [isFavorite]/[onToggleFavorite] bundled since [PlayerHost] only ever threads them through
 * together to [PlayerScreen]/[MiniPlayerBar] - see block 2.4 in the consolidated fix plan. */
data class PlayerFavoriteActions(
    val isFavorite: (M3uChannel) -> Boolean,
    val onToggleFavorite: (M3uChannel) -> Unit,
)

/** [epgState]/[iconPrefetchState] are both supplementary enrichment data neither [PlayerScreen] nor
 * [MiniPlayerBar] treats separately - see [PlayerFavoriteActions]. */
data class PlayerEnrichmentState(
    val epgState: EpgUiState,
    val iconPrefetchState: IconPrefetchUiState,
)

/**
 * Wraps the player screen in its own single-destination NavHost purely so its ViewModelStore -
 * and with it [PlayerViewModel] and the single ExoPlayer instance it owns - is torn down the
 * moment this composable leaves composition, instead of living for the app's whole process.
 *
 * [collapsed] switches between the fullscreen [PlayerScreen] and the small [MiniPlayerBar] -
 * critically, both are rendered by the *same* `composable("player")` destination/content lambda,
 * so [viewModel] (and the ExoPlayer instance it owns) is never recreated when toggling between
 * them - only [PlayerScreen]'s own gesture/brightness/keepScreenOn effects come and go with it
 * (via its existing `DisposableEffect`s), which is exactly the "only active while Expanded"
 * behavior the collapsed bar needs.
 */
@OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerHost(
    channels: List<M3uChannel>,
    startIndex: Int,
    collapsed: Boolean,
    onExit: () -> Unit,
    onTapCollapsed: () -> Unit,
    resolveIcon: suspend (M3uChannel) -> File?,
    favoriteActions: PlayerFavoriteActions,
    enrichment: PlayerEnrichmentState,
    modifier: Modifier = Modifier,
) {
    val (isFavorite, onToggleFavorite) = favoriteActions
    val (epgState, iconPrefetchState) = enrichment
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "player", modifier = modifier) {
        composable("player") {
            val viewModel: PlayerViewModel = viewModel()

            LaunchedEffect(channels, startIndex) {
                viewModel.start(channels, startIndex)
            }

            if (collapsed) {
                val iconRefreshKey: Any = (epgState.data != null) to iconPrefetchState.completedRuns
                MiniPlayerBar(
                    viewModel = viewModel,
                    resolveIcon = resolveIcon,
                    epgState = epgState,
                    iconRefreshKey = iconRefreshKey,
                    onTap = onTapCollapsed,
                    onClose = onExit,
                )
            } else {
                PlayerScreen(
                    viewModel = viewModel,
                    onExit = onExit,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    resolveIcon = resolveIcon,
                    epgState = epgState,
                    iconPrefetchState = iconPrefetchState,
                )
            }
        }
    }
}
