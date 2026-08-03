package com.uacastplayer.ui.channels
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.CurrentNextProgrammes
import com.uacastplayer.epg.EpgLookup
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.epg.ProgrammeProgress
import com.uacastplayer.playlist.ChannelListKeys
import com.uacastplayer.playlist.ChannelRowShape
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.NameQualityBadge
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.GlowStatusDot
import com.uacastplayer.ui.components.StatusPillVariant
import com.uacastplayer.ui.components.TrackProgress
import com.uacastplayer.ui.components.rememberDebounced
import com.uacastplayer.ui.components.rememberEntryStagger
import com.uacastplayer.ui.components.staggeredEntry
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.DurPress
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.ItemPadding
import com.uacastplayer.ui.theme.PressScaleRound
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.RadiusList
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.ChannelTileMinWidth
import com.uacastplayer.ui.theme.ChannelTileMinWidthLarge
import com.uacastplayer.ui.theme.HairlineInsetChannels
import com.uacastplayer.ui.theme.raisedSurface
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
) {
    var query by rememberSaveable(groupDisplayKey(grouped.group)) { mutableStateOf("") }
    val trimmedQuery = rememberDebounced(query.trim())
    val filteredChannels = remember(grouped.channels, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            grouped.channels
        } else {
            grouped.channels.filter { it.displayName.contains(trimmedQuery, ignoreCase = true) }
        }
    }
    // Replays when the filter changes: a search that narrows 400 rows to 3 is new content arriving,
    // and the wave is what makes that legible. Also covers opening a different group.
    val entryStagger = rememberEntryStagger(filteredChannels)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = GapM)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = UaTheme.palette.labelPrimary,
                )
            }
            Text(
                text = groupLabel(grouped.group),
                style = Title,
                color = UaTheme.palette.labelPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                ChannelLayoutMenu(selected = layout, onSelect = onLayoutChange)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.channels_search_hint)) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = UaTheme.palette.labelSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(RadiusField),
            colors = uaTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(top = GapM),
        )

        if (filteredChannels.isEmpty()) {
            NoSearchResults(trimmedQuery)
        } else if (layout == ChannelLayout.LIST) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = GapM)) {
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
                            .animateItem()
                            .staggeredEntry(stagger = entryStagger, key = entryKey, index = index)
                            .clip(shape)
                            .background(UaTheme.palette.surface1),
                    ) {
                        // nowMillis only changes once a minute (see EpgUiState), so this only
                        // recomputes on an actual minute tick or a channel/data change - not on
                        // every recomposition this row goes through while scrolling.
                        val programme = remember(channel.streamUrl, epgState.data, epgState.nowMillis) {
                            epgState.data?.let { EpgLookup.currentAndNext(it, channel, epgState.nowMillis) }
                        }
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
            val tileMinWidth = if (layout == ChannelLayout.LARGE_ICONS) ChannelTileMinWidthLarge else ChannelTileMinWidth
            LazyVerticalGrid(
                columns = GridCells.Adaptive(tileMinWidth),
                modifier = Modifier.fillMaxSize().padding(top = GapM),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItemsIndexed(
                    filteredChannels,
                    key = { index, channel -> ChannelListKeys.keyFor(index, channel.streamUrl) },
                ) { index, channel ->
                    val entryKey = ChannelListKeys.keyFor(index, channel.streamUrl)
                    ChannelTile(
                        channel = channel,
                        iconRefreshKey = iconRefreshKey,
                        resolveIcon = resolveIcon,
                        large = layout == ChannelLayout.LARGE_ICONS,
                        isLocked = isLocked(channel),
                        onClick = { onChannelClick(channel) },
                        onLongClick = { onLongPressChannel(channel) },
                        modifier = Modifier
                            .animateItem()
                            .staggeredEntry(stagger = entryStagger, key = entryKey, index = index),
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: M3uChannel,
    programme: CurrentNextProgrammes?,
    nowMillis: Long,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    density: ListDensity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isLocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressScaleRound else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "channelRowScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(ItemPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (density != ListDensity.MINIMAL) ChannelIcon(channel, resolveIcon, refreshKey = iconRefreshKey)
        Column(modifier = Modifier.weight(1f).padding(start = if (density == ListDensity.MINIMAL) 0.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.displayName,
                    style = BodyText.copy(fontFamily = UaTheme.palette.displayFontFamily),
                    color = UaTheme.palette.labelPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (density == ListDensity.FULL) {
                    // NameQualityBadge.detect runs a handful of regexes - only worth redoing when
                    // the name it's scanning actually changes, not on every recomposition this row
                    // goes through while scrolling.
                    val qualityBadge = remember(channel.displayName) { NameQualityBadge.detect(channel.displayName) }
                    qualityBadge?.let { badge ->
                        Text(
                            text = badge,
                            style = Caption,
                            color = UaTheme.palette.accentText,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            val current = programme?.current
            val effectiveStop = programme?.effectiveStopMillis
            if (density == ListDensity.FULL && current != null && effectiveStop != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    GlowStatusDot(variant = StatusPillVariant.Bad, size = 6.dp)
                    Text(
                        text = current.title,
                        style = Caption,
                        color = UaTheme.palette.labelSecondary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                TrackProgress(
                    progress = ProgrammeProgress.progress(current.startMillis, effectiveStop, nowMillis),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (isLocked) {
            Icon(
                AppIcons.Lock,
                contentDescription = stringResource(R.string.channels_channel_locked),
                tint = UaTheme.palette.labelSecondary,
                modifier = Modifier.size(18.dp).padding(end = 4.dp),
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                AppIcons.Favorites,
                contentDescription = stringResource(R.string.favorites_title),
                tint = if (isFavorite) UaTheme.palette.azure else UaTheme.palette.labelSecondary,
            )
        }
        Icon(
            AppIcons.ChevronDown,
            contentDescription = null,
            tint = UaTheme.palette.labelSecondary,
            modifier = Modifier.size(16.dp).padding(start = 2.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(
    channel: M3uChannel,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    large: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(RadiusList)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Inside a LazyVerticalGrid - shadow = false, see docs/DESIGN_SYSTEM.md "§D Depth".
            .raisedSurface(tileShape, UaTheme.palette.surface1, edgeColor = UaTheme.palette.hairline, shadow = false)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ChannelIcon(channel, resolveIcon, size = if (large) 64.dp else 44.dp, refreshKey = iconRefreshKey)
            Text(
                text = channel.displayName,
                style = Caption,
                color = UaTheme.palette.labelPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 8.dp),
            )
            // See ChannelRow's identical remember() - only worth redoing when the name actually
            // changes, not on every recomposition this tile goes through while scrolling a grid
            // (GRID is ChannelLayout.DEFAULT, so this runs for every user by default).
            val qualityBadge = remember(channel.displayName) { NameQualityBadge.detect(channel.displayName) }
            qualityBadge?.let { badge ->
                Text(
                    text = badge,
                    style = Caption,
                    color = UaTheme.palette.accentText,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // No favorite star here, unlike ChannelRow. A tile is roughly 150dp wide, and the star was
        // an IconButton with minimumInteractiveComponentSize() - a 48dp touch target around a 16dp
        // glyph, which reached well past the corner and over the channel's own icon. Tapping what
        // looked like the middle of the tile favorited the channel instead of opening it, and since
        // the grid is the default layout that was most users. The star showed favorite *state*
        // too, but not at a cost worth paying: a 16dp tint on a small tile was the least legible
        // place in the app to read it, and toggling now lives in ChannelActionsSheet on long press,
        // beside lock/unlock - where a per-channel action that is not "play this" belongs.
        if (isLocked) {
            Icon(
                AppIcons.Lock,
                contentDescription = stringResource(R.string.channels_channel_locked),
                tint = UaTheme.palette.labelSecondary,
                modifier = Modifier.align(Alignment.TopStart).size(16.dp),
            )
        }
    }
}
