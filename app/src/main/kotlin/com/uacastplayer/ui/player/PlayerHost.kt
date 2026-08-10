package com.uacastplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.uacastplayer.ui.components.openTransform
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    castArtworkUrl: (M3uChannel) -> String?,
    favoriteActions: PlayerFavoriteActions,
    enrichment: PlayerEnrichmentState,
    modifier: Modifier = Modifier,
) {
    val (isFavorite, onToggleFavorite) = favoriteActions
    val (epgState, iconPrefetchState) = enrichment

    val viewModel: PlayerViewModel = viewModel()

    // castArtworkUrl is deliberately not a key: it is a bound reference on the Activity-scoped
    // AppViewModel, so a fresh instance each recomposition would carry identical behavior while
    // restarting playback. It reads EPG/settings state at call time, so the one captured here does
    // not go stale as the EPG loads.
    LaunchedEffect(channels, startIndex) {
        viewModel.start(channels, startIndex, castArtworkUrl)
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

    // Registered here rather than in PlayerScreen because the mini bar is a player too: collapsing
    // to it and then pressing Home is the same stream from the same ExoPlayer, and an observer that
    // only existed on the full screen would miss it. This composable is the one that is mounted
    // whenever anything is playing at all.
    //
    // ON_STOP, not ON_PAUSE: a picture-in-picture window leaves the activity paused but visible,
    // and pausing the video the user is watching in it would be the opposite of the point. See
    // BackgroundPlaybackPolicy for what happens without this.
    //
    // A rotation also goes through ON_STOP, on its way to destroying and rebuilding the Activity -
    // the same reason releasePlayback() above is guarded. Without the same guard here, every
    // rotation would pause and then resume the stream, which on a live channel is a visible stall
    // rather than a no-op.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    if (context.findActivity()?.isChangingConfigurations != true) {
                        viewModel.onEnterBackground(context.isInPictureInPicture())
                    }
                Lifecycle.Event.ON_START -> viewModel.onReturnToForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                // Keyed on the request, not on `collapsed`: expanding the mini bar back to full
                // screen is a return to something already open, and replaying the opening there
                // would say a channel had just been picked when none had.
                modifier = Modifier.openTransform(key = channels to startIndex),
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

/** Below API 26 there is no picture-in-picture to be in, and the property does not exist. */
private fun Context.isInPictureInPicture(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && findActivity()?.isInPictureInPictureMode == true
