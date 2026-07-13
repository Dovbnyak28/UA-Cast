package com.uacastplayer.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.NameQualityBadge
import com.uacastplayer.playlist.PlaylistError
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.components.GlowStatusDot
import com.uacastplayer.ui.components.PlaylistImportControls
import com.uacastplayer.ui.components.StatusPillVariant
import com.uacastplayer.ui.components.TrackProgress
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Azure
import com.uacastplayer.ui.theme.AzureGradient
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.ChannelLogoRadius
import com.uacastplayer.ui.theme.ChannelLogoSize
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.Hairline
import com.uacastplayer.ui.theme.HairlineInsetChannels
import com.uacastplayer.ui.theme.ItemPadding
import com.uacastplayer.ui.theme.LabelPrimary
import com.uacastplayer.ui.theme.LabelSecondary
import com.uacastplayer.ui.theme.RadiusList
import com.uacastplayer.ui.theme.SectionLabel
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Surface1
import com.uacastplayer.ui.theme.Surface2
import com.uacastplayer.ui.theme.Title
import java.io.File

@Composable
fun ChannelsScreen(
    playlistState: PlaylistUiState,
    onLoadUrl: (String) -> Unit,
    onPickFile: () -> Unit,
    onChannelSelected: (channels: List<M3uChannel>, startIndex: Int) -> Unit,
    epgState: EpgUiState,
    iconPrefetchState: IconPrefetchUiState,
    resolveIcon: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    onChannelLayoutSelected: (ChannelLayout) -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flatChannels = remember(playlistState.groups) { playlistState.groups.flatMap { it.channels } }

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
        if (openGroup == null) {
            PlaylistImportControls(onLoadUrl = onLoadUrl, onPickFile = onPickFile)
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

        when {
            playlistState.isLoading -> LoadingState()
            playlistState.error != null -> ErrorState(playlistState.error)
            playlistState.hasChannels -> {
                val group = openGroup
                if (group == null) {
                    GroupsOverviewGrid(
                        groups = playlistState.groups,
                        layout = layout,
                        onLayoutChange = onChannelLayoutSelected,
                        onGroupClick = { openGroupKey = groupDisplayKey(it.group) },
                    )
                } else {
                    SingleGroupChannelList(
                        grouped = group,
                        epgState = epgState,
                        resolveIcon = resolveIcon,
                        density = density,
                        layout = layout,
                        onLayoutChange = onChannelLayoutSelected,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onBack = { openGroupKey = null },
                        onChannelClick = { channel ->
                            val index = flatChannels.indexOf(channel)
                            if (index >= 0) onChannelSelected(flatChannels, index)
                        },
                    )
                }
            }
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
                color = LabelSecondary,
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

@Composable
private fun EmptyState() {
    com.uacastplayer.ui.components.EmptyState(
        icon = AppIcons.Channels,
        title = stringResource(R.string.channels_empty_message),
        subtitle = stringResource(R.string.channels_empty_subtitle),
    )
}

/** Landing screen for the Channels tab: one card per group, showing its channel count. */
@Composable
private fun GroupsOverviewGrid(
    groups: List<GroupedChannels>,
    layout: ChannelLayout,
    onLayoutChange: (ChannelLayout) -> Unit,
    onGroupClick: (GroupedChannels) -> Unit,
) {
    val totalChannels = remember(groups) { groups.sumOf { it.channels.size } }
    val columns = if (layout == ChannelLayout.LIST) 1 else 2

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().padding(top = GapM),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "groups-header", span = { GridItemSpan(columns) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = stringResource(R.string.channels_groups_title), style = Title, color = LabelPrimary)
                    Text(
                        text = pluralStringResource(R.plurals.channel_groups_count, groups.size, groups.size) +
                            " · " +
                            pluralStringResource(R.plurals.channels_total_count, totalChannels, totalChannels),
                        style = Caption,
                        color = LabelSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                ChannelLayoutMenu(selected = layout, onSelect = onLayoutChange)
            }
        }
        items(groups, key = { groupDisplayKey(it.group) }) { grouped ->
            GroupCard(grouped = grouped, onClick = { onGroupClick(grouped) })
        }
    }
}

@Composable
private fun GroupCard(grouped: GroupedChannels, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusList))
            .background(Surface1)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Tv, contentDescription = null, tint = Azure, modifier = Modifier.size(22.dp))
        }
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(AzureGradient),
        )
        Text(
            text = groupLabel(grouped.group),
            style = BodyText,
            color = LabelPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.channels_total_count, grouped.channels.size, grouped.channels.size),
            style = Caption,
            color = LabelSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** One already-selected group's channels: back + title + search, then the list/grid rendering. */
@Composable
private fun SingleGroupChannelList(
    grouped: GroupedChannels,
    epgState: EpgUiState,
    resolveIcon: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    onLayoutChange: (ChannelLayout) -> Unit,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onBack: () -> Unit,
    onChannelClick: (M3uChannel) -> Unit,
) {
    var query by rememberSaveable(groupDisplayKey(grouped.group)) { mutableStateOf("") }
    val trimmedQuery = query.trim()
    val filteredChannels = remember(grouped.channels, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            grouped.channels
        } else {
            grouped.channels.filter { it.displayName.contains(trimmedQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = GapM), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = LabelPrimary)
            }
            Text(
                text = groupLabel(grouped.group),
                style = Title,
                color = LabelPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            ChannelLayoutMenu(selected = layout, onSelect = onLayoutChange)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.channels_search_hint)) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = LabelSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = GapM),
        )

        if (filteredChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.channels_no_search_results, trimmedQuery),
                    style = BodyText,
                    color = LabelSecondary,
                )
            }
        } else if (layout == ChannelLayout.LIST) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = GapM)) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusList)).background(Surface1),
                    ) {
                        filteredChannels.forEachIndexed { index, channel ->
                            val programme = epgState.data?.let { EpgLookup.currentAndNext(it, channel, epgState.nowMillis) }
                            ChannelRow(
                                channel = channel,
                                programme = programme,
                                nowMillis = epgState.nowMillis,
                                resolveIcon = resolveIcon,
                                density = density,
                                isFavorite = isFavorite(channel),
                                onToggleFavorite = { onToggleFavorite(channel) },
                                onClick = { onChannelClick(channel) },
                            )
                            if (index != filteredChannels.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = HairlineInsetChannels)
                                        .height(1.dp)
                                        .background(Hairline),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val columns = if (layout == ChannelLayout.LARGE_ICONS) 2 else 3
            LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize().padding(top = GapM)) {
                items(filteredChannels, key = { it.streamUrl }) { channel ->
                    ChannelTile(
                        channel = channel,
                        resolveIcon = resolveIcon,
                        large = layout == ChannelLayout.LARGE_ICONS,
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
                tint = Azure,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChannelLayout.entries.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.labelRes()),
                            color = if (isSelected) Azure else LabelPrimary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            option.icon(),
                            contentDescription = null,
                            tint = if (isSelected) Azure else LabelSecondary,
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

@Composable
private fun ChannelRow(
    channel: M3uChannel,
    programme: CurrentNextProgrammes?,
    nowMillis: Long,
    resolveIcon: suspend (M3uChannel) -> File?,
    density: ListDensity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(ItemPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (density != ListDensity.MINIMAL) ChannelIcon(channel, resolveIcon)
        Column(modifier = Modifier.weight(1f).padding(start = if (density == ListDensity.MINIMAL) 0.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.displayName,
                    style = BodyText,
                    color = LabelPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (density == ListDensity.FULL) {
                    NameQualityBadge.detect(channel.displayName)?.let { badge ->
                        Text(
                            text = badge,
                            style = Caption,
                            color = com.uacastplayer.ui.theme.Azure2,
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
                        color = LabelSecondary,
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
                contentDescription = null,
                tint = if (isFavorite) com.uacastplayer.ui.theme.Azure else LabelSecondary,
            )
        }
        Icon(
            AppIcons.ChevronDown,
            contentDescription = null,
            tint = LabelSecondary,
            modifier = Modifier.size(16.dp).padding(start = 2.dp),
        )
    }
}

@Composable
private fun ChannelTile(
    channel: M3uChannel,
    resolveIcon: suspend (M3uChannel) -> File?,
    large: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelIcon(channel, resolveIcon, size = if (large) 64.dp else 44.dp)
        Text(
            text = channel.displayName,
            style = Caption,
            color = LabelPrimary,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ChannelIcon(channel: M3uChannel, resolveIcon: suspend (M3uChannel) -> File?, size: androidx.compose.ui.unit.Dp = ChannelLogoSize) {
    val iconFile by produceState<File?>(initialValue = null, key1 = channel.streamUrl) {
        value = resolveIcon(channel)
    }
    if (iconFile != null) {
        AsyncImage(
            model = iconFile,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(ChannelLogoRadius)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(ChannelLogoRadius))
                .background(com.uacastplayer.ui.theme.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsFor(channel.displayName),
                style = Caption,
                color = LabelSecondary,
            )
        }
    }
}

private fun initialsFor(name: String): String =
    name.trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

private fun groupDisplayKey(group: ChannelGroup): String = when (group) {
    is ChannelGroup.Known -> group.key
    is ChannelGroup.Custom -> group.rawTitle
    ChannelGroup.Ungrouped -> "ungrouped"
}

@Composable
private fun groupLabel(group: ChannelGroup): String = when (group) {
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
