package com.uacastplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.uacastplayer.data.playlist.withPlaylistCpu
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.parentalcontrol.PlayerChannelAccess
import com.uacastplayer.player.PlayerRequest
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.player.PlayerRequestViewModel

/** Restoration owns only a compact marker; a new user open/close always wins over its CPU hop. */
@Composable
internal fun RestorePlayerRequest(
    saved: SavedPlayerRequest?, playlist: PlaylistUiState, ready: Boolean, locked: Set<String>, unlocked: Boolean,
    owner: PlayerRequestViewModel, currentMarker: () -> SavedPlayerRequest?, persistedKey: () -> String?,
    onRestored: () -> Unit, onMissing: () -> Unit,
) {
    LaunchedEffect(saved, playlist.groups, ready, locked, unlocked) {
        val marker = saved ?: return@LaunchedEffect
        if (owner.request.value != null || !ready) return@LaunchedEffect
        if (marker.localVideo != null) {
            owner.open(PlayerRequest.forLocalVideo(marker.localVideo))
            onRestored()
        } else if (playlist.hasChannels) {
            val restored = withPlaylistCpu {
                PlayerChannelAccess.forRestore(playlist.channels, marker.preferredChannelKey(persistedKey()),
                    locked, FavoriteKey::of, unlocked, ready)
            }
            if (currentMarker() != marker || owner.request.value != null) return@LaunchedEffect
            if (restored != null) {
                owner.open(PlayerRequest(restored.channels, restored.startIndex))
                onRestored()
            } else onMissing()
        }
    }
}
