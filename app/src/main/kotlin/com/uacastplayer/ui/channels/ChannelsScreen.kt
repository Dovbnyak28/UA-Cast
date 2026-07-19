package com.uacastplayer.ui.channels
import com.uacastplayer.ui.theme.UaTheme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.uacastplayer.R
import com.uacastplayer.data.prefs.ChannelLayout
import com.uacastplayer.data.prefs.ListDensity
import com.uacastplayer.epg.CurrentNextProgrammes
import com.uacastplayer.epg.EpgLookup
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.epg.ProgrammeProgress
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.ChannelListKeys
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.playlist.ChannelRowShape
import com.uacastplayer.playlist.ChannelSearch
import com.uacastplayer.playlist.ChannelSearchOutcome
import com.uacastplayer.playlist.ChannelSearchResult
import com.uacastplayer.playlist.GroupOrderPolicy
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.NameQualityBadge
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.GlowStatusDot
import com.uacastplayer.ui.components.GroupIconCollage
import com.uacastplayer.ui.components.IconHeader
import com.uacastplayer.ui.components.IconTierBanner
import com.uacastplayer.ui.components.StatusPillVariant
import com.uacastplayer.ui.components.rememberDebounced
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.components.TrackProgress
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.ChannelLogoRadius
import com.uacastplayer.ui.theme.ChannelLogoSize
import com.uacastplayer.ui.theme.ChannelTileMinWidth
import com.uacastplayer.ui.theme.ChannelTileMinWidthLarge
import com.uacastplayer.ui.theme.DurPress
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GroupTileMinWidth
import com.uacastplayer.ui.theme.HairlineInsetChannels
import com.uacastplayer.ui.theme.ItemPadding
import com.uacastplayer.ui.theme.PressScaleRound
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RadiusList
import com.uacastplayer.ui.theme.SectionLabel
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.raisedSurface
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    playlistState: PlaylistUiState,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    epgState: EpgUiState,
    iconPrefetchState: IconPrefetchUiState,
    resolveIcon: suspend (M3uChannel) -> File?,
    cachedIconFile: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onRefreshPlaylist: () -> Unit,
    showIconTierBanner: Boolean,
    onEnableIcons: () -> Unit,
    onDismissIconTierBanner: () -> Unit,
    pinnedGroupKeys: Set<String>,
    hiddenGroupKeys: Set<String>,
    onPinGroup: (String) -> Unit,
    onHideGroup: (String) -> Unit,
    onClearGroupOverride: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flatChannels = remember(playlistState.groups) { playlistState.groups.flatMap { it.channels } }
    var guideChannel by remember { mutableStateOf<M3uChannel?>(null) }

    // Forces every ChannelIcon/GroupIconCollage in this screen to re-resolve when either signal
    // fires: EPG data arriving unlocks its icon-URL source (see AppViewModel.resolveChannelIcon),
    // and a completed prefetch run may have just written new files for channels that previously
    // resolved to nothing. Deliberately NOT nowMillis or anything else that changes often - a
    // re-resolve on every recomposition would defeat the point of ChannelIcon's own caching.
    val iconRefreshKey: Any = (epgState.data != null) to iconPrefetchState.completedRuns

    // Landing on a groups overview (like the rest of the bottom-nav tabs, this is per-tab UI state,
    // not app state - it deliberately resets to the overview on process death, unlike the playlist
    // itself). Storing the group's stable key (not the GroupedChannels/ChannelGroup value) keeps
    // this Saveable and lets the open group re-resolve against a reloaded playlist.
    var openGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    val openGroup = remember(playlistState.groups, openGroupKey) {
        openGroupKey?.let { key -> playlistState.groups.firstOrNull { groupDisplayKey(it.group) == key } }
    }
    if (openGroupKey != null && openGroup == null) {
        openGroupKey = null
    }
    if (openGroup != null) {
        BackHandler { openGroupKey = null }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = ScreenHPadding)) {
        if (showIconTierBanner) {
            IconTierBanner(
                onEnableIcons = onEnableIcons,
                onDismiss = onDismissIconTierBanner,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        if (iconPrefetchState.isRunning) {
            val progress = if (iconPrefetchState.total > 0) {
                iconPrefetchState.completed.toFloat() / iconPrefetchState.total
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        // Only a URL-sourced playlist has anything to pull-to-refresh from; a file import would
        // just spin and settle back with nothing having happened, which reads as broken.
        if (playlistState.sourceUrl != null) {
            PullToRefreshBox(
                isRefreshing = playlistState.isLoading && playlistState.hasChannels,
                onRefresh = onRefreshPlaylist,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                ChannelsContent(
                    playlistState = playlistState,
                    openGroup = openGroup,
                    flatChannels = flatChannels,
                    epgState = epgState,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    cachedIconFile = cachedIconFile,
                    density = density,
                    layout = layout,
                    onChannelLayoutSelected = onChannelLayoutSelected,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onOpenGroup = { openGroupKey = groupDisplayKey(it.group) },
                    onCloseGroup = { openGroupKey = null },
                    onChannelSelected = onChannelSelected,
                    onLongPressChannel = { guideChannel = it },
                    pinnedGroupKeys = pinnedGroupKeys,
                    hiddenGroupKeys = hiddenGroupKeys,
                    onPinGroup = onPinGroup,
                    onHideGroup = onHideGroup,
                    onClearGroupOverride = onClearGroupOverride,
                )
            }
        } else {
            ChannelsContent(
                playlistState = playlistState,
                openGroup = openGroup,
                flatChannels = flatChannels,
                epgState = epgState,
                iconRefreshKey = iconRefreshKey,
                resolveIcon = resolveIcon,
                cachedIconFile = cachedIconFile,
                density = density,
                layout = layout,
                onChannelLayoutSelected = onChannelLayoutSelected,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onOpenGroup = { openGroupKey = groupDisplayKey(it.group) },
                onCloseGroup = { openGroupKey = null },
                onChannelSelected = onChannelSelected,
                onLongPressChannel = { guideChannel = it },
                pinnedGroupKeys = pinnedGroupKeys,
                hiddenGroupKeys = hiddenGroupKeys,
                onPinGroup = onPinGroup,
                onHideGroup = onHideGroup,
                onClearGroupOverride = onClearGroupOverride,
            )
        }
    }

    guideChannel?.let { channel ->
        com.uacastplayer.ui.epg.EpgGuideSheet(
            channel = channel,
            epgData = epgState.data,
            nowMillis = epgState.nowMillis,
            onDismiss = { guideChannel = null },
        )
    }
}

@Composable
private fun ChannelsContent(
    playlistState: PlaylistUiState,
    openGroup: GroupedChannels?,
    flatChannels: List<M3uChannel>,
    epgState: EpgUiState,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    cachedIconFile: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onOpenGroup: (GroupedChannels) -> Unit,
    onCloseGroup: () -> Unit,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    onLongPressChannel: (M3uChannel) -> Unit,
    pinnedGroupKeys: Set<String>,
    hiddenGroupKeys: Set<String>,
    onPinGroup: (String) -> Unit,
    onHideGroup: (String) -> Unit,
    onClearGroupOverride: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // hasChannels wins over both isLoading and error: a reload in progress or one that
            // just failed must not hide channels already on screen - see applyPlaylistOutcome/
            // PlaylistOutcomeReducer, which deliberately preserve them for exactly this reason.
            playlistState.hasChannels -> {
                val group = openGroup
                if (group == null) {
                    GroupsOverviewGrid(
                        groups = playlistState.groups,
                        layout = layout,
                        onLayoutChange = onChannelLayoutSelected,
                        onGroupClick = onOpenGroup,
                        iconRefreshKey = iconRefreshKey,
                        resolveIcon = resolveIcon,
                        cachedIconFile = cachedIconFile,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onChannelClick = { channel ->
                            val index = flatChannels.indexOf(channel)
                            if (index >= 0) onChannelSelected(flatChannels, index)
                        },
                        pinnedGroupKeys = pinnedGroupKeys,
                        hiddenGroupKeys = hiddenGroupKeys,
                        onPinGroup = onPinGroup,
                        onHideGroup = onHideGroup,
                        onClearGroupOverride = onClearGroupOverride,
                    )
                } else {
                    SingleGroupChannelList(
                        grouped = group,
                        epgState = epgState,
                        iconRefreshKey = iconRefreshKey,
                        resolveIcon = resolveIcon,
                        density = density,
                        layout = layout,
                        onLayoutChange = onChannelLayoutSelected,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onBack = onCloseGroup,
                        onChannelClick = { channel ->
                            val index = flatChannels.indexOf(channel)
                            if (index >= 0) onChannelSelected(flatChannels, index)
                        },
                        onLongPressChannel = onLongPressChannel,
                    )
                }
            }
            playlistState.isLoading -> LoadingState()
            playlistState.error != null -> ErrorState(playlistState.error)
            else -> EmptyState()
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.playlist_loading),
                style = BodyText,
                color = UaTheme.palette.labelSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ErrorState(error: PlaylistError) {
    val message = when (error) {
        PlaylistError.SizeLimitExceeded -> stringResource(R.string.playlist_error_size_limit)
        is PlaylistError.Http -> stringResource(R.string.playlist_error_http, error.code)
        PlaylistError.Network -> stringResource(R.string.playlist_error_network)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = BodyText, color = MaterialTheme.colorScheme.error)
    }
}

/** No playlist loaded at all - unlike [ErrorState] (a load that failed) or the search's
 * [NoSearchResults] (a query with no matches), this dead end needs a way out, but the action
 * itself now lives on Home (see [com.uacastplayer.ui.home.HomeScreen]'s own empty state) rather
 * than being duplicated here. */
@Composable
private fun EmptyState() {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        IconHeader(
            icon = AppIcons.Channels,
            title = stringResource(R.string.channels_empty_message),
            subtitle = stringResource(R.string.channels_empty_subtitle),
        )
    }
}

/**
 * Landing screen for the Channels tab: one card per group, showing its channel count. A non-blank
 * query switches to a flat, whole-playlist [ChannelSearch] result list instead - browsing group by
 * group doesn't scale to playlists with thousands of channels.
 */
@Composable
private fun GroupsOverviewGrid(
    groups: List<GroupedChannels>,
    layout: ChannelLayout,
    onLayoutChange: (ChannelLayout) -> Unit,
    onGroupClick: (GroupedChannels) -> Unit,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    cachedIconFile: suspend (M3uChannel) -> File?,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onChannelClick: (M3uChannel) -> Unit,
    pinnedGroupKeys: Set<String>,
    hiddenGroupKeys: Set<String>,
    onPinGroup: (String) -> Unit,
    onHideGroup: (String) -> Unit,
    onClearGroupOverride: (String) -> Unit,
) {
    // Search (below) deliberately keeps searching every group, hidden ones included - hiding a
    // group only affects the overview grid; its channels stay reachable via global search.
    val orderedGroups = remember(groups, pinnedGroupKeys, hiddenGroupKeys) {
        GroupOrderPolicy.order(groups, pinnedGroupKeys, hiddenGroupKeys)
    }
    var groupActionsFor by remember { mutableStateOf<GroupedChannels?>(null) }
    val totalChannels = remember(orderedGroups) { orderedGroups.sumOf { it.channels.size } }
    // LIST mode stays a literal single column (a list of group cards); GRID/LARGE_ICONS lets the
    // width fit as many ~GroupTileMinWidth tiles as the screen allows, unlike a fixed column count.
    val gridCells = if (layout == ChannelLayout.LIST) GridCells.Fixed(1) else GridCells.Adaptive(GroupTileMinWidth)

    var query by rememberSaveable { mutableStateOf("") }
    // Debounced: this search runs across the whole playlist (not just one group), so re-running it
    // on every keystroke while typing causes visible jank on larger playlists.
    val trimmedQuery = rememberDebounced(query.trim())
    val searchOutcome = remember(groups, trimmedQuery) {
        if (trimmedQuery.isEmpty()) null else ChannelSearch.search(groups, trimmedQuery)
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = GapM)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.channels_groups_title),
                    style = Title,
                    color = UaTheme.palette.labelPrimary,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.channel_groups_count,
                        orderedGroups.size,
                        orderedGroups.size,
                    ) +
                        " · " +
                        pluralStringResource(R.plurals.channels_total_count, totalChannels, totalChannels),
                    style = Caption,
                    color = UaTheme.palette.labelSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ChannelLayoutMenu(selected = layout, onSelect = onLayoutChange)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.channels_search_all_hint)) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = UaTheme.palette.labelSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(RadiusField),
            colors = uaTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(top = GapM),
        )

        when (searchOutcome) {
            null -> LazyVerticalGrid(
                columns = gridCells,
                modifier = Modifier.fillMaxSize().padding(top = GapM),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(orderedGroups, key = { groupDisplayKey(it.group) }) { grouped ->
                    GroupCard(
                        grouped = grouped,
                        iconRefreshKey = iconRefreshKey,
                        cachedIconFile = cachedIconFile,
                        onClick = { onGroupClick(grouped) },
                        onLongClick = { groupActionsFor = grouped },
                    )
                }
            }

            is ChannelSearchOutcome.Matches -> if (searchOutcome.results.isEmpty()) {
                NoSearchResults(trimmedQuery)
            } else {
                ChannelSearchResultsList(
                    results = searchOutcome.results,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onChannelClick = onChannelClick,
                )
            }

            is ChannelSearchOutcome.TooBroad -> {
                Text(
                    text = stringResource(R.string.channels_search_too_broad, ChannelSearch.MAX_RESULTS),
                    style = Caption,
                    color = UaTheme.palette.labelSecondary,
                    modifier = Modifier.padding(top = GapM),
                )
                ChannelSearchResultsList(
                    results = searchOutcome.results,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onChannelClick = onChannelClick,
                )
            }
        }
    }

    groupActionsFor?.let { grouped ->
        val key = groupDisplayKey(grouped.group)
        val isPinned = key in pinnedGroupKeys
        GroupActionsSheet(
            groupLabel = groupLabel(grouped.group),
            isPinned = isPinned,
            onTogglePin = { if (isPinned) onClearGroupOverride(key) else onPinGroup(key) },
            onHide = { onHideGroup(key) },
            onDismiss = { groupActionsFor = null },
        )
    }
}

@Composable
private fun NoSearchResults(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.channels_no_search_results, query),
            style = BodyText,
            color = UaTheme.palette.labelSecondary,
        )
    }
}

/** Flat, cross-group results for a whole-playlist [ChannelSearch] - each row shows which group a
 * match came from, since it's no longer implied by an already-open group screen. */
@Composable
private fun ChannelSearchResultsList(
    results: List<ChannelSearchResult>,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onChannelClick: (M3uChannel) -> Unit,
) {
    // One LazyColumn item per result - see the itemsIndexed usage in SingleGroupChannelList for
    // why this must not collapse back into a single item wrapping a forEachIndexed Column.
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = GapM)) {
        itemsIndexed(
            results,
            key = { index, result -> ChannelListKeys.keyFor(index, result.channel.streamUrl) },
        ) { index, result ->
            val rounding = ChannelRowShape.roundingFor(index, results.lastIndex)
            val shape = RoundedCornerShape(
                topStart = if (rounding.top) RadiusList else 0.dp,
                topEnd = if (rounding.top) RadiusList else 0.dp,
                bottomStart = if (rounding.bottom) RadiusList else 0.dp,
                bottomEnd = if (rounding.bottom) RadiusList else 0.dp,
            )
            // flat by design: each row's own rounding varies (see ChannelRowShape) so adjacent
            // rows read as one continuous card with hairline dividers between them - a per-row
            // raisedSurface border would draw a seam at every row instead.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(UaTheme.palette.surface1),
            ) {
                ChannelSearchResultRow(
                    result = result,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    isFavorite = isFavorite(result.channel),
                    onToggleFavorite = { onToggleFavorite(result.channel) },
                    onClick = { onChannelClick(result.channel) },
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
}

@Composable
private fun ChannelSearchResultRow(
    result: ChannelSearchResult,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(ItemPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelIcon(result.channel, resolveIcon, refreshKey = iconRefreshKey)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = result.channel.displayName,
                style = BodyText,
                color = UaTheme.palette.labelPrimary,
                maxLines = 1,
            )
            Text(
                text = groupLabel(result.group),
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                AppIcons.Favorites,
                contentDescription = stringResource(R.string.favorites_title),
                tint = if (isFavorite) UaTheme.palette.azure else UaTheme.palette.labelSecondary,
            )
        }
    }
}

private sealed class GroupBadge {
    data class Glyph(val icon: ImageVector) : GroupBadge()
    data class Label(val text: String) : GroupBadge()
}

private fun groupBadge(group: ChannelGroup, label: String): GroupBadge {
    val upper = label.uppercase()
    return when {
        (group is ChannelGroup.Known && group.key == ChannelGroup.KEY_KIDS) ||
            upper.contains("ДИТ") || upper.contains("KIDS") || upper.contains("ДЕТ") -> GroupBadge.Glyph(AppIcons.Kids)
        upper.contains("4K") -> GroupBadge.Label("4K")
        upper.contains("HD") -> GroupBadge.Label("HD")
        else -> GroupBadge.Glyph(AppIcons.Tv)
    }
}

@Composable
private fun GroupCard(
    grouped: GroupedChannels,
    iconRefreshKey: Any,
    cachedIconFile: suspend (M3uChannel) -> File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressScaleRound else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
        label = "groupCardScale",
    )
    val shape = RoundedCornerShape(RadiusList)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            // Inside a LazyVerticalGrid (GroupsOverviewGrid) - shadow = false, see
            // docs/DESIGN_SYSTEM.md "§D Depth".
            .raisedSurface(shape, UaTheme.palette.surface1, edgeColor = UaTheme.palette.hairline, shadow = false)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(16.dp),
    ) {
        GroupIconCollage(
            channels = grouped.channels,
            cachedIconFile = cachedIconFile,
            refreshKey = iconRefreshKey,
            size = 44.dp,
            fallback = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .raisedSurface(RoundedCornerShape(RadiusItem), UaTheme.palette.surface2, shadow = false),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val badge = groupBadge(grouped.group, groupLabel(grouped.group))) {
                        is GroupBadge.Glyph -> Icon(
                            badge.icon,
                            contentDescription = null,
                            tint = UaTheme.palette.azure,
                            modifier = Modifier.size(22.dp),
                        )
                        is GroupBadge.Label -> Text(
                            text = badge.text,
                            style = Caption,
                            color = UaTheme.palette.accentText,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                }
            },
        )
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(UaTheme.palette.accentGradient),
        )
        Text(
            text = groupLabel(grouped.group),
            style = BodyText.copy(fontFamily = UaTheme.palette.displayFontFamily),
            color = UaTheme.palette.labelPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.channels_total_count, grouped.channels.size, grouped.channels.size),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** One already-selected group's channels: back + title + search, then the list/grid rendering. */
@Composable
private fun SingleGroupChannelList(
    grouped: GroupedChannels,
    epgState: EpgUiState,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    onLayoutChange: (ChannelLayout) -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
                ) { _, channel ->
                    ChannelTile(
                        channel = channel,
                        iconRefreshKey = iconRefreshKey,
                        resolveIcon = resolveIcon,
                        large = layout == ChannelLayout.LARGE_ICONS,
                        isFavorite = isFavorite(channel),
                        onToggleFavorite = { onToggleFavorite(channel) },
                        onClick = { onChannelClick(channel) },
                    )
                }
            }
        }
    }
}

/** Toolbar control that switches [ChannelLayout], shared by the groups overview and a single group. */
@Composable
private fun ChannelLayoutMenu(selected: ChannelLayout, onSelect: (ChannelLayout) -> Unit) {
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

@Composable
private fun ChannelTile(
    channel: M3uChannel,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    large: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val tileShape = RoundedCornerShape(RadiusList)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Inside a LazyVerticalGrid - shadow = false, see docs/DESIGN_SYSTEM.md "§D Depth".
            .raisedSurface(tileShape, UaTheme.palette.surface1, edgeColor = UaTheme.palette.hairline, shadow = false)
            .clickable(onClick = onClick)
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
            NameQualityBadge.detect(channel.displayName)?.let { badge ->
                Text(
                    text = badge,
                    style = Caption,
                    color = UaTheme.palette.accentText,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
        ) {
            Icon(
                AppIcons.Favorites,
                contentDescription = stringResource(R.string.favorites_title),
                tint = if (isFavorite) UaTheme.palette.azure else UaTheme.palette.labelSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Shared with SettingsScreen's hidden-groups list - not just ChannelsScreen's own use. */
@Composable
internal fun groupLabel(group: ChannelGroup): String = when (group) {
    is ChannelGroup.Known -> stringResource(group.labelRes())
    is ChannelGroup.Custom -> group.rawTitle
    ChannelGroup.Ungrouped -> stringResource(R.string.group_ungrouped)
}

private fun ChannelGroup.Known.labelRes(): Int = when (key) {
    ChannelGroup.KEY_MOVIES -> R.string.group_movies
    ChannelGroup.KEY_SERIES -> R.string.group_series
    ChannelGroup.KEY_NEWS -> R.string.group_news
    ChannelGroup.KEY_SPORTS -> R.string.group_sports
    ChannelGroup.KEY_KIDS -> R.string.group_kids
    ChannelGroup.KEY_MUSIC -> R.string.group_music
    ChannelGroup.KEY_DOCUMENTARY -> R.string.group_documentary
    ChannelGroup.KEY_ENTERTAINMENT -> R.string.group_entertainment
    ChannelGroup.KEY_SCIENCE -> R.string.group_science
    ChannelGroup.KEY_RELIGION -> R.string.group_religion
    ChannelGroup.KEY_REGIONAL -> R.string.group_regional
    else -> R.string.group_ungrouped
}
