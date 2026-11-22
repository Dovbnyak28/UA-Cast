package app.zorik.player.ui.channels

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.zorik.player.core.settings.ChannelLayout

/** Both lazy providers contain one search/category header, then the same ordered channel list. */
@Composable
internal fun SyncCatalogScrollAnchor(
    layout: ChannelLayout,
    channelCount: Int,
    list: LazyListState,
    grid: LazyGridState,
) {
    // Keep the last rendered mode when leaving a group too: another group's layout selection
    // must not make this group reopen at an unrelated, previously unused grid/list position.
    var previous by rememberSaveable { mutableStateOf(layout) }
    LaunchedEffect(layout) {
        if (previous != layout && channelCount > 0) {
            val index = if (previous == ChannelLayout.LIST) list.firstVisibleItemIndex else grid.firstVisibleItemIndex
            val target = index.coerceIn(0, channelCount)
            // Offset zero keeps the anchor visible even when the new row has a different height.
            if (layout == ChannelLayout.LIST) list.scrollToItem(target) else grid.scrollToItem(target)
        }
        // Do not advance previous before a suspended scroll completes: cancellation by a rapid
        // reverse switch must preserve the last valid source, not a grid still waiting for layout.
        previous = layout
    }
}
