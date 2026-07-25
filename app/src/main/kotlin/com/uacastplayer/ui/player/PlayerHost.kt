package com.uacastplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
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
 * Hosts the player UI (fullscreen [PlayerScreen] or the small [MiniPlayerBar]) over a single,
 * Activity-scoped [PlayerViewModel].
 *
 * This used to wrap the player in its own nested single-destination NavHost purely to scope the
 * ViewModelStore. That was the OOM's root cause: every time this composable re-entered composition
 * (close-then-reopen, and each configuration change) it constructed a *new* NavController with a
 * *new* ViewModelStore, producing a second [PlayerViewModel] - and the second ExoPlayer it owns -
 * while the previous one was still being torn down. The logs showed two 90-second-buffer ExoPlayers
 * alive in one process at once, which is exactly enough to exhaust the heap.
 *
 * Now [viewModel] resolves against the host Activity's ViewModelStore (the owner `setContent`
 * installs, since there is no longer a NavHost above this call to shadow it). That store is stable
 * across close/reopen and configuration changes, so exactly one [PlayerViewModel]/ExoPlayer ever
 * exists (enforced defensively by [PlayerViewModel]'s liveInstances guard). [collapsed] just swaps
 * which child renders over that same instance; [PlayerViewModel.start] switches channel within it.
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

    val viewModel: PlayerViewModel = viewModel()

    LaunchedEffect(channels, startIndex) {
        viewModel.start(channels, startIndex)
    }

    // The Activity-scoped ViewModel outlives this composable, so its ExoPlayer would otherwise keep
    // holding decoders and buffers after the player is fully closed. Freeing playback resources when
    // PlayerHost leaves composition releases them without destroying the (reused) ViewModel. Guarded
    // against configuration changes: on rotation PlayerHost also leaves composition, but the
    // ViewModel is deliberately retained and playback must resume, so releasing there would be wrong.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        onDispose {
            if (context.findActivity()?.isChangingConfigurations != true) {
                viewModel.releasePlayback()
            }
        }
    }

    Box(modifier = modifier) {
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

/** Unwraps the (possibly themed/wrapped) Compose [Context] to the host Activity, so
 * [Activity.isChangingConfigurations] can distinguish a real close from a rotation. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
