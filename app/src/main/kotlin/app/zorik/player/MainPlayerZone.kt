package app.zorik.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zorik.player.favorites.FavoriteKey
import app.zorik.player.player.PlayerContainerStateMachine
import app.zorik.player.player.PlayerRequest
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.ui.player.PlayerEnrichmentState
import app.zorik.player.ui.player.PlayerFavoriteActions
import app.zorik.player.ui.player.PlayerHost
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.navigationBarHeight
import app.zorik.player.ui.theme.GlassTabBarVerticalPadding

/** The always-mounted player container - collects only what [PlayerHost] itself needs, so cast
 * state, settings, the backup summary etc. (all owned by [ScaffoldZone]) never touch this scope. */
@Composable
internal fun BoxScope.PlayerZone(
    viewModel: AppViewModel,
    playerRequest: PlayerRequest?,
    playerContainerState: PlayerContainerStateMachine.State,
    usesNavigationRail: Boolean,
    onPlayerContainerStateChange: (PlayerContainerStateMachine.State) -> Unit,
    onClosePlayer: () -> Unit,
    onOpacityChanged: (Boolean) -> Unit,
) {
    val request = playerRequest ?: return
    val epgState by viewModel.epgState.collectAsStateWithLifecycle()
    val iconPrefetchState by viewModel.iconPrefetchState.collectAsStateWithLifecycle()
    // Collected here, rather than delegating to viewModel::isFavorite, for the same reason as
    // ScaffoldZone's copy: PlayerScreen's favorite button tints itself from isFavorite(currentChannel),
    // and a plain function call reading StateFlow.value is not a Compose state read. Without a
    // subscription the star kept its old tint after being tapped until something unrelated
    // recomposed this zone - in practice the next EPG minute tick, so up to a minute late. Unlike
    // the flows deliberately kept out of this scope, favorites only change when the user actually
    // toggles one, which is exactly when the player *should* recompose.
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteKeys = remember(favorites) { favorites.mapTo(HashSet(favorites.size)) { it.key } }
    val isFavorite = remember(favoriteKeys) {
        { channel: M3uChannel -> FavoriteKey.of(channel) in favoriteKeys }
    }
    val isPlayerExpanded = playerContainerState == PlayerContainerStateMachine.State.EXPANDED

    BackHandler(enabled = isPlayerExpanded) {
        onPlayerContainerStateChange(
            PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Back),
        )
    }
    // Once collapsed, Back belongs to the visible browse/settings screen. The explicit X
    // stops playback; navigating between pages must not silently close the mini player.
    PlayerHost(
        request = request,
        collapsed = !isPlayerExpanded,
        onExit = onClosePlayer,
        onCollapse = {
            onPlayerContainerStateChange(
                PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Back),
            )
        },
        onTapCollapsed = {
            onPlayerContainerStateChange(
                PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Tap),
            )
        },
        resolveIcon = viewModel::resolveChannelIcon,
        castArtworkUrl = viewModel::castArtworkUrlFor,
        favoriteActions = PlayerFavoriteActions(
            isFavorite = isFavorite,
            onToggleFavorite = viewModel::toggleFavorite,
        ),
        enrichment = PlayerEnrichmentState(epgState = epgState, iconPrefetchState = iconPrefetchState),
        onOpacityChanged = onOpacityChanged,
        modifier = if (isPlayerExpanded) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = if (usesNavigationRail) ScreenHPadding else 0.dp)
                // The rail does not consume bottom space. Keeping the old tab-bar offset on
                // tablets made the mini player float unnecessarily high above the gesture bar.
                .padding(
                    bottom = if (usesNavigationRail) GapM else {
                        navigationBarHeight(LocalDensity.current.fontScale) + GlassTabBarVerticalPadding * 2
                    },
                )
                .fillMaxWidth()
                .widthIn(max = 720.dp)
        },
    )
}
