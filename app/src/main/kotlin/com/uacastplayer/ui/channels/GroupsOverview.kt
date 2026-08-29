package com.uacastplayer.ui.channels
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.playlist.withPlaylistCpu
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.guidedtour.GuidedTourKeys
import com.uacastplayer.ui.guidedtour.guidedTourTarget
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.ChannelSearch
import com.uacastplayer.playlist.ChannelSearchOutcome
import com.uacastplayer.playlist.GroupOrderPolicy
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.groupDisplayKey
import com.uacastplayer.ui.components.rememberDebounced
import com.uacastplayer.ui.components.rememberEntryStagger
import com.uacastplayer.ui.components.staggeredEntry
import com.uacastplayer.ui.components.uaTextFieldColors
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.DUR_PRESS
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GroupTileMinWidth
import com.uacastplayer.ui.theme.PRESS_SCALE_ROUND
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RadiusList
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.raisedSurface
import java.io.File

/**
 * Landing screen for the Channels tab: one card per group, showing its channel count. A non-blank
 * query switches to a flat, whole-playlist [ChannelSearch] result list instead - browsing group by
 * group doesn't scale to playlists with thousands of channels.
 */
@Composable
internal fun GroupsOverviewGrid(
    groups: List<GroupedChannels>,
    /**
     * Hoisted into [com.uacastplayer.ui.channels.ChannelsScreen] rather than remembered here.
     *
     * Opening a group swaps this whole grid out for the single-group list, so a state remembered
     * inside it is discarded the moment a group is opened and starts again at the top when the user
     * comes back - which, with a few dozen groups, means hunting for the folder they were just in
     * after every channel they watch. Held above the switch, the position outlives the excursion.
     */
    gridState: LazyGridState,
    layout: ChannelLayout,
    onLayoutChange: (ChannelLayout) -> Unit,
    onGroupClick: (GroupedChannels) -> Unit,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
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
    // Keyed on the ordered list, so pinning or hiding a group replays the wave over the new order
    // rather than leaving the moved cards as the only static things on screen.
    val entryStagger = rememberEntryStagger(orderedGroups)
    val totalChannels = remember(orderedGroups) { orderedGroups.sumOf { it.channels.size } }
    // LIST mode stays a literal single column (a list of group cards); GRID/LARGE_ICONS lets the
    // width fit as many ~GroupTileMinWidth tiles as the screen allows, unlike a fixed column count.
    val gridCells = if (layout == ChannelLayout.LIST) GridCells.Fixed(1) else GridCells.Adaptive(GroupTileMinWidth)

    var query by rememberSaveable { mutableStateOf("") }
    // Debounced so a burst of keystrokes costs one search rather than one per character - this
    // searches the whole playlist, not just one group.
    val trimmedQuery = rememberDebounced(query.trim())
    // ...and run off the composition thread, because debouncing only reduces how OFTEN the scan
    // happens, not what it costs when it does: inside remember{} every search blocked the frame
    // that composed it, for as long as a full-playlist scan takes. produceState keeps showing the
    // previous results while the next scan runs, so the list never flashes empty mid-typing.
    val searchOutcome by produceState<ChannelSearchOutcome?>(null, groups, trimmedQuery) {
        value = if (trimmedQuery.isEmpty()) {
            null
        } else {
            // Search belongs to the same bounded CPU lane as playlist parsing. Media3 recovery can
            // saturate Dispatchers.Default; making search wait behind it left the Channels screen
            // apparently empty even though the playlist itself had already loaded.
            withPlaylistCpu { ChannelSearch.search(groups, trimmedQuery) }
        }
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
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            AppIcons.Close,
                            contentDescription = stringResource(R.string.cache_clear_button),
                            tint = UaTheme.palette.labelSecondary,
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(RadiusField),
            colors = uaTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GapM)
                .guidedTourTarget(GuidedTourKeys.CHANNEL_SEARCH),
        )

        // Read the delegate once into a local: `when` cannot smart-cast a delegated property, and
        // this also pins one value for the whole branch instead of re-reading a state that the
        // background search can update between reads.
        when (val outcome = searchOutcome) {
            null -> LazyVerticalGrid(
                columns = gridCells,
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(top = GapM),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(orderedGroups, key = { _, grouped -> groupDisplayKey(grouped.group) }) { index, grouped ->
                    val key = groupDisplayKey(grouped.group)
                    GroupCard(
                        grouped = grouped,
                        onClick = { onGroupClick(grouped) },
                        onLongClick = { groupActionsFor = grouped },
                        modifier = Modifier
                            .animateItem()
                            .staggeredEntry(stagger = entryStagger, key = key, index = index),
                    )
                }
            }

            is ChannelSearchOutcome.Matches -> if (outcome.results.isEmpty()) {
                NoSearchResults(trimmedQuery)
            } else {
                ChannelSearchResultsList(
                    results = outcome.results,
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
                    results = outcome.results,
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

/**
 * The quality of a group, when its name states one - never its category.
 *
 * Matched on the label rather than on [ChannelGroup.Known], because quality is not a category: a
 * provider's "Sport FHD" is a sports group that happens to be high definition, and the label is
 * what the user reads.
 */
private fun groupQualityBadge(label: String): String? {
    val upper = label.uppercase()
    return when {
        upper.contains("4K") -> "4K"
        upper.contains("HD") -> "HD"
        else -> null
    }
}

/** Presentation-only category cue; custom/provider groups keep a neutral channels glyph. */
private fun groupIcon(group: ChannelGroup): ImageVector = when (group) {
    is ChannelGroup.Known -> when (group.key) {
        ChannelGroup.KEY_MOVIES,
        ChannelGroup.KEY_SERIES,
        ChannelGroup.KEY_NEWS,
        ChannelGroup.KEY_DOCUMENTARY,
        ChannelGroup.KEY_ENTERTAINMENT,
        -> AppIcons.Tv
        ChannelGroup.KEY_SPORTS -> AppIcons.Play
        ChannelGroup.KEY_KIDS -> AppIcons.Kids
        ChannelGroup.KEY_MUSIC -> AppIcons.AudioTrack
        ChannelGroup.KEY_SCIENCE,
        ChannelGroup.KEY_RELIGION,
        ChannelGroup.KEY_REGIONAL,
        -> AppIcons.Globe
        else -> AppIcons.Channels
    }
    is ChannelGroup.Custom,
    ChannelGroup.Ungrouped,
    -> AppIcons.Channels
}

@Composable
private fun GroupCard(
    grouped: GroupedChannels,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE_ROUND else 1f,
        animationSpec = tween(DUR_PRESS, easing = EaseSpring),
        label = "groupCardScale",
    )
    val shape = RoundedCornerShape(RadiusList)
    Column(
        modifier = modifier
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
            .heightIn(min = 92.dp)
            .padding(14.dp),
    ) {
        val label = groupLabel(grouped.group)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(RadiusItem))
                    .background(UaTheme.palette.azure.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = groupIcon(grouped.group),
                    contentDescription = null,
                    tint = UaTheme.palette.azure,
                    modifier = Modifier.size(19.dp),
                )
            }
            Text(
                text = label,
                style = BodyText.copy(fontFamily = UaTheme.palette.displayFontFamily),
                color = UaTheme.palette.labelPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            val quality = groupQualityBadge(label)
            if (quality != null) {
                Text(
                    text = quality,
                    style = Caption,
                    color = UaTheme.palette.accentText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(UaTheme.palette.azure.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = pluralStringResource(R.plurals.channels_total_count, grouped.channels.size, grouped.channels.size),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
