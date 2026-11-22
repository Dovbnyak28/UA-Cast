package app.zorik.player.ui.channels
import app.zorik.player.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.data.playlist.filterPlaylistChannels
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.epg.EpgLookup
import app.zorik.player.epg.EpgUiState
import app.zorik.player.playlist.ChannelListKeys
import app.zorik.player.playlist.ChannelRowShape
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.groupDisplayKey
import app.zorik.player.ui.components.rememberDebounced
import app.zorik.player.ui.components.animationsAllowed
import app.zorik.player.ui.components.rememberEntryStagger
import app.zorik.player.ui.components.staggeredEntry
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.RadiusList
import app.zorik.player.ui.theme.ChannelTileMinWidth
import app.zorik.player.ui.theme.ChannelTileMinWidthLarge
import app.zorik.player.ui.theme.HairlineInsetChannels
import app.zorik.player.ui.theme.raisedSurface
import java.io.File

/** One already-selected group's channels: back + title + search, then the list/grid rendering. */
@Composable
internal fun SingleGroupChannelList(
    grouped: GroupedChannels,
    epgState: EpgUiState,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    onLayoutChange: (ChannelLayout) -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    isLocked: (M3uChannel) -> Boolean,
    onBack: () -> Unit,
    onChannelClick: (M3uChannel) -> Unit,
    onLongPressChannel: (M3uChannel) -> Unit,
    onGuide: (M3uChannel) -> Unit = {},
    categoryStrip: @Composable () -> Unit = {},
) {
    var query by rememberSaveable(groupDisplayKey(grouped.group)) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val trimmedQuery = rememberDebounced(query.trim())
    val filteredChannels by produceState(grouped.channels, grouped.channels, trimmedQuery) {
        value = if (trimmedQuery.isEmpty()) {
            grouped.channels
        } else {
            // One provider can put every channel in a single group. Filtering that list belongs
            // beside whole-playlist search, not in the composition that draws the text field.
            filterPlaylistChannels(grouped.channels, trimmedQuery)
        }
    }
    SyncCatalogScrollAnchor(layout, filteredChannels.size, listState, gridState)
    // Replays when the filter changes: a search that narrows 400 rows to 3 is new content arriving,
    // and the wave is what makes that legible. Also covers opening a different group.
    val entryStagger = rememberEntryStagger(filteredChannels)
    val animateItems = animationsAllowed()

    val header: @Composable () -> Unit = {
        ChannelCatalogHeader(query, { query = it }, categoryStrip)
    }
    Column(Modifier.fillMaxSize()) {
        ChannelCatalogToolbar(grouped, layout, onLayoutChange, onBack)
        Box(Modifier.weight(1f)) {
        if (filteredChannels.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header", contentType = "header") { header() }
                item(key = "empty", contentType = "empty") {
                    Text(stringResource(R.string.channels_no_search_results, trimmedQuery), style = BodyText,
                        modifier = Modifier.padding(vertical = GapM))
                    androidx.compose.material3.TextButton(onClick = { query = "" }) {
                        Text(stringResource(R.string.channels_clear_search))
                    }
                }
            }
        } else if (layout == ChannelLayout.LIST) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(top = GapM),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GapM)) {
                item(key = "header", contentType = "header") { header() }
                // One LazyColumn item per channel - NOT a single item wrapping a forEachIndexed
                // Column - so LazyColumn actually virtualizes a large group instead of composing
                // every row up front regardless of what's on screen. Corner rounding is computed
                // per row (see ChannelRowShape) so the list still reads as one continuous rounded
                // card despite each row being its own item; don't collapse this back into one
                // item for a simpler-looking Column, that reintroduces the non-virtualized
                // composition this was written to fix.
                itemsIndexed(
                    filteredChannels,
                    key = { index, channel -> ChannelListKeys.keyFor(index, channel.streamUrl) },
                    contentType = { _, _ -> "channel" },
                ) { index, channel ->
                    val rounding = ChannelRowShape.roundingFor(index, filteredChannels.lastIndex)
                    val shape = RoundedCornerShape(
                        topStart = if (rounding.top) RadiusList else 0.dp,
                        topEnd = if (rounding.top) RadiusList else 0.dp,
                        bottomStart = if (rounding.bottom) RadiusList else 0.dp,
                        bottomEnd = if (rounding.bottom) RadiusList else 0.dp,
                    )
                    // flat by design: each row's own rounding varies (see ChannelRowShape) so
                    // adjacent rows read as one continuous card with hairline dividers between
                    // them - a per-row raisedSurface border would draw a seam at every row instead.
                    val entryKey = ChannelListKeys.keyFor(index, channel.streamUrl)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (animateItems) Modifier.animateItem() else Modifier)
                            .staggeredEntry(entryStagger, entryKey, index, animationsEnabled = animateItems)
                            .clip(shape)
                            .background(UaTheme.palette.surface1),
                    ) {
                        // nowMillis only changes once a minute (see EpgUiState), so this only
                        // recomputes on an actual minute tick or a channel/data change - not on
                        // every recomposition this row goes through while scrolling.
                        val programme = rememberChannelProgramme(channel, epgState)
                        ChannelRow(
                            channel = channel,
                            programme = programme,
                            nowMillis = epgState.nowMillis,
                            iconRefreshKey = iconRefreshKey,
                            resolveIcon = resolveIcon,
                            density = density,
                            isFavorite = isFavorite(channel),
                            onToggleFavorite = { onToggleFavorite(channel) },
                            isLocked = isLocked(channel),
                            onClick = { onChannelClick(channel) },
                            onLongClick = { onLongPressChannel(channel) },
                            onGuide = { onGuide(channel) },
                        )
                        if (!rounding.bottom) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = HairlineInsetChannels)
                                    .height(1.dp)
                                    .background(UaTheme.palette.hairline),
                            )
                        }
                    }
                }
            }
        } else {
            val tileMinWidth = if (layout == ChannelLayout.LARGE_ICONS) {
                ChannelTileMinWidthLarge
            } else {
                // Two independent 48dp controls plus horizontal padding must fit in every tile.
                maxOf(ChannelTileMinWidth, 128.dp)
            }
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            LazyVerticalGrid(
                columns = GridCells.Adaptive(
                    if (fontScale >= LARGE_CATALOG_FONT_SCALE) ChannelTileMinWidthLarge else tileMinWidth),
                state = gridState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GapM),
                modifier = Modifier.fillMaxSize().padding(top = GapM),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    contentType = "header") { header() }
                gridItemsIndexed(
                    filteredChannels,
                    key = { index, channel -> ChannelListKeys.keyFor(index, channel.streamUrl) },
                    contentType = { _, _ -> "channel" },
                ) { index, channel ->
                    val entryKey = ChannelListKeys.keyFor(index, channel.streamUrl)
                    ChannelTile(
                        channel = channel,
                        iconRefreshKey = iconRefreshKey,
                        resolveIcon = resolveIcon,
                        large = layout == ChannelLayout.LARGE_ICONS,
                        isLocked = isLocked(channel),
                        programme = rememberChannelProgramme(channel, epgState),
                        nowMillis = epgState.nowMillis,
                        isFavorite = isFavorite(channel),
                        onToggleFavorite = { onToggleFavorite(channel) },
                        onGuide = { onGuide(channel) },
                        onClick = { onChannelClick(channel) },
                        onLongClick = { onLongPressChannel(channel) },
                        modifier = Modifier
                            .then(if (animateItems) Modifier.animateItem() else Modifier)
                            .staggeredEntry(entryStagger, entryKey, index, animationsEnabled = animateItems),
                    )
                }
            }
        }
        }
    }
}

/** Toolbar control that switches [ChannelLayout], shared by the groups overview and a single group. */
@Composable
internal fun ChannelLayoutMenu(selected: ChannelLayout, onSelect: (ChannelLayout) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = selected.icon(),
                contentDescription = stringResource(R.string.settings_channel_layout_label),
                tint = UaTheme.palette.azure,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChannelLayout.entries.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.labelRes()),
                            color = if (isSelected) UaTheme.palette.azure else UaTheme.palette.labelPrimary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            option.icon(),
                            contentDescription = null,
                            tint = if (isSelected) UaTheme.palette.azure else UaTheme.palette.labelSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (!isSelected) onSelect(option)
                    },
                )
            }
        }
    }
}

private fun ChannelLayout.icon(): ImageVector = when (this) {
    ChannelLayout.LIST -> AppIcons.ViewList
    ChannelLayout.GRID -> AppIcons.GridView
    ChannelLayout.LARGE_ICONS -> AppIcons.LargeIcons
}

private fun ChannelLayout.labelRes(): Int = when (this) {
    ChannelLayout.LIST -> R.string.channel_layout_list
    ChannelLayout.GRID -> R.string.channel_layout_grid
    ChannelLayout.LARGE_ICONS -> R.string.channel_layout_large_icons
}

