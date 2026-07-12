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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.uacastplayer.ui.theme.AppIcons
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
    var urlInput by rememberSaveable { mutableStateOf("") }
    val flatChannels = remember(playlistState.groups) { playlistState.groups.flatMap { it.channels } }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(stringResource(R.string.playlist_url_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onLoadUrl(urlInput) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.playlist_load_button))
            }
            OutlinedButton(onClick = onPickFile) {
                Text(stringResource(R.string.playlist_browse_file))
            }
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
            playlistState.hasChannels -> ChannelGroupList(
                groups = playlistState.groups,
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
                style = MaterialTheme.typography.bodyMedium,
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
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.channels_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            for (grouped in groups) {
                item(key = "header-${groupDisplayKey(grouped.group)}") { GroupHeader(grouped.group) }
                items(grouped.channels, key = { it.streamUrl }) { channel ->
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
                }
            }
        }
    } else {
        val columns = if (layout == ChannelLayout.LARGE_ICONS) 2 else 3
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
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
        text = groupLabel(group),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (density == ListDensity.MINIMAL) 4.dp else 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (density != ListDensity.MINIMAL) ChannelIcon(channel, resolveIcon)
            Text(
                text = channel.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = if (density == ListDensity.MINIMAL) 0.dp else 12.dp),
            )
            if (density == ListDensity.FULL) {
                NameQualityBadge.detect(channel.displayName)?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    AppIcons.Favorites,
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        }
        val current = programme?.current
        val effectiveStop = programme?.effectiveStopMillis
        if (density == ListDensity.FULL && current != null && effectiveStop != null) {
            Text(
                text = current.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            LinearProgressIndicator(
                progress = { ProgrammeProgress.progress(current.startMillis, effectiveStop, nowMillis) },
                modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp),
            )
        }
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
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ChannelIcon(channel: M3uChannel, resolveIcon: suspend (M3uChannel) -> File?, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val iconFile by produceState<File?>(initialValue = null, key1 = channel.streamUrl) {
        value = resolveIcon(channel)
    }
    if (iconFile != null) {
        AsyncImage(
            model = iconFile,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsFor(channel.displayName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
