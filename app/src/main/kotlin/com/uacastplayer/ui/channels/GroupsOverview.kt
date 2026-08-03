package com.uacastplayer.ui.channels
import com.uacastplayer.ui.theme.UaTheme

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.prefs.ChannelLayout
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
import com.uacastplayer.ui.theme.DurPress
import com.uacastplayer.ui.theme.EaseSpring
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GroupTileMinWidth
import com.uacastplayer.ui.theme.PressScaleRound
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RadiusList
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.raisedSurface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Landing screen for the Channels tab: one card per group, showing its channel count. A non-blank
 * query switches to a flat, whole-playlist [ChannelSearch] result list instead - browsing group by
 * group doesn't scale to playlists with thousands of channels.
 */
@Composable
internal fun GroupsOverviewGrid(
    groups: List<GroupedChannels>,
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
            withContext(Dispatchers.Default) { ChannelSearch.search(groups, trimmedQuery) }
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
            singleLine = true,
            shape = RoundedCornerShape(RadiusField),
            colors = uaTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(top = GapM),
        )

        // Read the delegate once into a local: `when` cannot smart-cast a delegated property, and
        // this also pins one value for the whole branch instead of re-reading a state that the
        // background search can update between reads.
        when (val outcome = searchOutcome) {
            null -> LazyVerticalGrid(
                columns = gridCells,
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

private val GroupBadgeSize = 44.dp

/**
 * A group card's badge is decided by the group's *category*, never by what happens to be in the
 * playlist or the icon cache.
 *
 * It used to be a collage of up to 4 channel logos from the group, drawn only from files already on
 * disk. Two things were wrong with that. The obvious one: the same group rendered differently on two
 * launches, because the collage was a picture of the cache rather than of the group - and with a
 * large playlist the cache is a moving target. The worse one: a group whose first channels have no
 * cached logo fell through to the badge below, so on one screen some cards carried four tiny
 * mismatched provider logos and others carried the curated illustration, with nothing telling the
 * user why. A category badge is the same on every launch, on every playlist, for every user.
 */
private sealed class GroupBadge {
    data class Artwork(@DrawableRes val drawableRes: Int) : GroupBadge()
    data class Label(val text: String) : GroupBadge()
}

// Curated, category-specific artwork - one per ChannelGroup.Known key - takes priority over the
// generic TV artwork so real playlist categories (movies/sports/kids/...) read at a glance instead
// of all looking identical. Custom/Ungrouped groups fall through to the generic TV artwork (see
// groupBadge's else branch); only quality-only groups (4K/HD) get a text label instead.
private fun knownGroupArtwork(key: String): Int? = when (key) {
    ChannelGroup.KEY_MOVIES -> R.drawable.group_icon_movies
    ChannelGroup.KEY_SERIES -> R.drawable.group_icon_series
    ChannelGroup.KEY_NEWS -> R.drawable.group_icon_news
    ChannelGroup.KEY_SPORTS -> R.drawable.group_icon_sports
    ChannelGroup.KEY_KIDS -> R.drawable.group_icon_kids
    ChannelGroup.KEY_MUSIC -> R.drawable.group_icon_music
    ChannelGroup.KEY_DOCUMENTARY -> R.drawable.group_icon_documentary
    ChannelGroup.KEY_ENTERTAINMENT -> R.drawable.group_icon_entertainment
    ChannelGroup.KEY_SCIENCE -> R.drawable.group_icon_science
    ChannelGroup.KEY_RELIGION -> R.drawable.group_icon_religion
    ChannelGroup.KEY_REGIONAL -> R.drawable.group_icon_generic_tv
    else -> null
}

private fun groupBadge(group: ChannelGroup, label: String): GroupBadge {
    val upper = label.uppercase()
    val artwork = (group as? ChannelGroup.Known)?.let { knownGroupArtwork(it.key) }
    return when {
        artwork != null -> GroupBadge.Artwork(artwork)
        upper.contains("ДИТ") || upper.contains("KIDS") || upper.contains("ДЕТ") ->
            GroupBadge.Artwork(R.drawable.group_icon_kids)
        upper.contains("4K") -> GroupBadge.Label("4K")
        upper.contains("HD") -> GroupBadge.Label("HD")
        // Custom/ungrouped playlist categories with no illustrated match (e.g. a provider's own
        // "Other"/regional bucket) still get the generic TV artwork instead of the old thin Tv
        // glyph, so no card on this screen is left looking unfinished next to the illustrated ones.
        else -> GroupBadge.Artwork(R.drawable.group_icon_generic_tv)
    }
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
        targetValue = if (pressed) PressScaleRound else 1f,
        animationSpec = tween(DurPress, easing = EaseSpring),
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
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(GroupBadgeSize)
                .raisedSurface(RoundedCornerShape(RadiusItem), UaTheme.palette.surface2, shadow = false),
            contentAlignment = Alignment.Center,
        ) {
            when (val badge = groupBadge(grouped.group, groupLabel(grouped.group))) {
                is GroupBadge.Artwork -> Image(
                    painter = painterResource(badge.drawableRes),
                    contentDescription = null,
                    modifier = Modifier.size(GroupBadgeSize),
                )
                is GroupBadge.Label -> Text(
                    text = badge.text,
                    style = Caption,
                    color = UaTheme.palette.accentText,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
