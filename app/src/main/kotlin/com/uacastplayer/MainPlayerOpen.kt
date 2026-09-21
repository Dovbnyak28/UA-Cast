package com.uacastplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.uacastplayer.player.PlayerRequest
import com.uacastplayer.playlist.M3uChannel

/** Unknown restrictions are not an empty lock list. Only the latest tap proceeds once ready.
 * The pending action and PIN callback remain composition-owned, never retaining an Activity. */
@Composable
internal fun OpenPlayerWhenReady(
    pending: PlayerRequest?,
    ready: Boolean,
    onConsumed: () -> Unit,
    isLocked: (M3uChannel) -> Boolean,
    requireUnlock: (() -> Unit) -> Unit,
    onOpen: (PlayerRequest) -> Unit,
) {
    LaunchedEffect(pending, ready) {
        if (pending == null || !ready) return@LaunchedEffect
        onConsumed()
        val channel = pending.channels.getOrNull(pending.startIndex) ?: return@LaunchedEffect
        if (isLocked(channel)) requireUnlock { onOpen(pending) } else onOpen(pending)
    }
}
