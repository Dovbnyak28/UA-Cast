package com.uacastplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.player.PlayerContainerStateMachine
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.player.PlayerEnrichmentState
import com.uacastplayer.ui.player.PlayerFavoriteActions
import com.uacastplayer.ui.player.PlayerHost
import com.uacastplayer.ui.nav.AdaptiveRootLayout
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GlassTabBarHeight
import com.uacastplayer.ui.theme.GlassTabBarVerticalPadding
import com.uacastplayer.ui.theme.ScreenHPadding

/** The always-mounted player container - collects only what [PlayerHost] itself needs, so cast
 * state, settings, the backup summary etc. (all owned by [ScaffoldZone]) never touch this scope. */
@Composable
internal fun BoxScope.PlayerZone(
    viewModel: AppViewModel,
    playerRequest: PlayerRequest?,
    playerContainerState: PlayerContainerStateMachine.State,
    onPlayerContainerStateChange: (PlayerContainerStateMachine.State) -> Unit,
    onClosePlayer: () -> Unit,
    backHandlerBlocked: Boolean,
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
    val isPlayerCollapsed = playerContainerState == PlayerContainerStateMachine.State.COLLAPSED

    BackHandler(enabled = isPlayerExpanded) {
        onPlayerContainerStateChange(
            PlayerContainerStateMachine.reduce(playerContainerState, PlayerContainerStateMachine.Event.Back),
        )
    }
    // Disabled while a sheet-like screen (Help/Terms/AddPlaylist) has its own BackHandler active, so
    // back closes that first - otherwise this collapsed-bar handler, composed later, would
    // intercept back before the sheet's own does.
    BackHandler(enabled = isPlayerCollapsed && !backHandlerBlocked) {
        onClosePlayer()
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val usesNavigationRail = maxWidth >= AdaptiveRootLayout.MEDIUM_WIDTH_DP.dp
        PlayerHost(
            channels = request.channels,
            startIndex = request.startIndex,
            collapsed = !isPlayerExpanded,
            onExit = onClosePlayer,
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
            modifier = if (isPlayerExpanded) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = ScreenHPadding)
                    // The rail does not consume bottom space. Keeping the old tab-bar offset on
                    // tablets made the mini player float unnecessarily high above the gesture bar.
                    .padding(
                        bottom = if (usesNavigationRail) GapM else {
                            GlassTabBarHeight + GlassTabBarVerticalPadding * 2
                        },
                    )
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            },
        )
    }
}
