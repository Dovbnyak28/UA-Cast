package app.zorik.player.ui.tv

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import app.zorik.player.favorites.FavoriteKey
import app.zorik.player.playlist.M3uChannel
import kotlinx.coroutines.flow.first

val LocalTvBrowseActive = compositionLocalOf { true }

private const val FOCUS_RETRY_FRAMES = 10

internal fun resolveTvFocusIndex(channels: List<M3uChannel>, key: String?, previousIndex: Int): Int {
    if (channels.isEmpty() || key == null) return 0
    val nearby = previousIndex.coerceIn(channels.indices)
    return if (FavoriteKey.matches(channels[nearby], key)) nearby
        else channels.indexOfFirst { FavoriteKey.matches(it, key) }.takeIf { it >= 0 } ?: nearby
}

internal class TvBrowseFocus(
    val targetIndex: Int,
    requester: FocusRequester,
    onPlaced: () -> Unit,
    val onSelected: (M3uChannel, Int) -> Unit,
) {
    val targetModifier: Modifier = Modifier.focusRequester(requester).onGloballyPositioned { onPlaced() }
}

internal fun Modifier.tvBrowseFocus(focus: TvBrowseFocus, index: Int): Modifier =
    if (index == focus.targetIndex) then(focus.targetModifier) else this

/** Only a player return (or restored screen) requests focus. Typing/filtering never steals it. */
@Composable
internal fun rememberTvBrowseFocus(channels: List<M3uChannel>, query: String, grid: LazyGridState): TvBrowseFocus {
    var key by rememberSaveable { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf(true) }
    val active = LocalTvBrowseActive.current
    val requester = remember { FocusRequester() }
    val target = remember(channels, key, index) { resolveTvFocusIndex(channels, key, index) }
    val shouldFocus by rememberUpdatedState(active && pending && (key != null || query.isBlank()))
    val request = { if (shouldFocus && requester.requestFocus()) pending = false }
    LaunchedEffect(active) { if (!active) pending = true }
    LaunchedEffect(active, pending, channels, target, query) {
        if (shouldFocus && channels.isNotEmpty()) {
            if (grid.layoutInfo.visibleItemsInfo.none { it.index == target }) grid.scrollToItem(target)
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.index == target } }.first { it }
            // requestFocus() returns false while the focus owner is still handing focus back from
            // the player overlay that is leaving; one attempt then left the grid unfocused for good
            // (seen on a slow CI runner). Retry on the next few frames, bounded, until it takes.
            repeat(FOCUS_RETRY_FRAMES) {
                withFrameNanos { }
                request()
                if (!pending) return@LaunchedEffect
            }
        }
    }
    return TvBrowseFocus(target, requester, request) { channel, selectedIndex ->
        key = FavoriteKey.of(channel)
        index = selectedIndex
    }
}
