package com.uacastplayer.ui.channels

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.uacastplayer.ui.components.SegmentedControl
import com.uacastplayer.ui.components.StatusPillVariant
import com.uacastplayer.ui.components.TrackProgress
import com.uacastplayer.ui.theme.AppIcons
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
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flatChannels = remember(playlistState.groups) { playlistState.groups.flatMap { it.channels } }
    var selectedCategory by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = ScreenHPadding)) {
        PlaylistImportControls(onLoadUrl = onLoadUrl, onPickFile = onPickFile)

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
                val categories = remember(playlistState.groups) { playlistState.groups.map { it.group } }
                if (categories.size > 1) {
                    val labels = listOf(stringResource(R.string.channels_category_all)) + categories.map { groupLabel(it) }
                    SegmentedControl(
                        options = labels,
                        selectedIndex = selectedCategory.coerceIn(0, labels.lastIndex),
                        onSelected = { selectedCategory = it },
                        modifier = Modifier.padding(top = GapM),
                    )
                }
                val visibleGroups = if (selectedCategory == 0 || categories.size <= 1) {
                    playlistState.groups
                } else {
                    val target = categories.getOrNull(selectedCategory - 1)
                    playlistState.groups.filter { it.group == target }
                }
                ChannelGroupList(
                    groups = visibleGroups,
                    epgState = epgState,
                    resolveIcon = resolveIcon,
                    density = density,
                    layout = layout,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onChannelClick = { channel ->
                        val index = flatChannels.indexOf(channel)
                        if (index >= 0) onChannelSelected(flatChannels, index)
                    },
                )
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

@Composable
private fun ChannelGroupList(
    groups: List<GroupedChannels>,
    epgState: EpgUiState,
    resolveIcon: suspend (M3uChannel) -> File?,
    density: ListDensity,
    layout: ChannelLayout,
    isFavorite: (M3uChannel) -> Boolean,
    onToggleFavorite: (M3uChannel) -> Unit,
    onChannelClick: (M3uChannel) -> Unit,
) {
    if (layout == ChannelLayout.LIST) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = GapM)) {
            for (grouped in groups) {
                item(key = "header-${groupDisplayKey(grouped.group)}") { GroupHeader(grouped.group) }
                item(key = "inset-${groupDisplayKey(grouped.group)}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RadiusList))
                            .background(Surface1),
                    ) {
                        grouped.channels.forEachIndexed { index, channel ->
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
                            if (index != grouped.channels.lastIndex) {
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
        }
    } else {
        val columns = if (layout == ChannelLayout.LARGE_ICONS) 2 else 3
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize().padding(top = GapM)) {
            for (grouped in groups) {
                item(key = "header-${groupDisplayKey(grouped.group)}", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
                    GroupHeader(grouped.group)
                }
                items(grouped.channels, key = { it.streamUrl }) { channel ->
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

@Composable
private fun GroupHeader(group: ChannelGroup) {
    Text(
        text = groupLabel(group).uppercase(),
        style = SectionLabel,
        color = LabelSecondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
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
