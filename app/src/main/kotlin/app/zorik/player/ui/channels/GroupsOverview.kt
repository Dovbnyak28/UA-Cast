package app.zorik.player.ui.channels
import app.zorik.player.ui.theme.CategoryTones
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.theme.panelSurface
import app.zorik.player.ui.theme.CardTitle

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.data.playlist.searchPlaylistChannels
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.guidedtour.GuidedTourKeys
import app.zorik.player.ui.guidedtour.guidedTourTarget
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.ChannelSearch
import app.zorik.player.playlist.ChannelSearchOutcome
import app.zorik.player.playlist.GroupOrderPolicy
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.groupDisplayKey
import app.zorik.player.ui.components.controlFeedback
import app.zorik.player.ui.tv.tvFocus
import app.zorik.player.ui.components.rememberDebounced
import app.zorik.player.ui.components.animationsAllowed
import app.zorik.player.ui.components.rememberEntryStagger
import app.zorik.player.ui.components.staggeredEntry
import app.zorik.player.ui.components.uaTextFieldColors
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.GapM
import app.zorik.player.ui.theme.GroupTileMinWidth
import app.zorik.player.ui.theme.PRESS_SCALE_ROUND
import app.zorik.player.ui.theme.RadiusField
import app.zorik.player.ui.theme.RadiusItem
import app.zorik.player.ui.theme.RadiusList
import app.zorik.player.ui.theme.Title
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
     * Hoisted into [app.zorik.player.ui.channels.ChannelsScreen] rather than remembered here.
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
    onGuide: (M3uChannel) -> Unit = {},
    epgState: app.zorik.player.epg.EpgUiState = app.zorik.player.epg.EpgUiState(),
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
    val animateItems = animationsAllowed()
    val totalChannels = remember(orderedGroups) { orderedGroups.sumOf { it.channels.size } }
    // LIST mode stays a literal single column (a list of group cards); GRID/LARGE_ICONS lets the
    // width fit as many ~GroupTileMinWidth tiles as the screen allows, unlike a fixed column count.
    val categoryWidth = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= LARGE_CATALOG_FONT_SCALE) {
        224.dp
    } else 128.dp
    val gridCells = if (layout == ChannelLayout.LIST) GridCells.Fixed(1) else GridCells.Adaptive(categoryWidth)

    var query by rememberSaveable { mutableStateOf("") }
    // Opening a result must also release the search field. It used to keep focus behind the
    // player, so the soft keyboard stayed up over the video, swallowed the first system Back, and
    // after a rotation re-opened over a landscape screen it shrank to almost nothing.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val openSearchResult: (M3uChannel) -> Unit = { channel ->
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        onChannelClick(channel)
    }
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
            searchPlaylistChannels(groups, trimmedQuery)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = GapM)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            AppIcons.Close,
                            contentDescription = stringResource(R.string.channels_clear_search),
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
                            .then(if (animateItems) Modifier.animateItem() else Modifier)
                            .staggeredEntry(entryStagger, key, index, animationsEnabled = animateItems),
                    )
                }
            }

            is ChannelSearchOutcome.Matches -> if (outcome.results.isEmpty()) {
                NoSearchResults(trimmedQuery, onClearSearch = { query = "" })
            } else {
                ChannelSearchResultsList(
                    results = outcome.results,
                    iconRefreshKey = iconRefreshKey,
                    resolveIcon = resolveIcon,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onChannelClick = openSearchResult,
                    onGuide = onGuide,
                    epgState = epgState,
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
                    onChannelClick = openSearchResult,
                    onGuide = onGuide,
                    epgState = epgState,
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

/**
 * The category glyph on a tinted square, shared by the groups grid and the home shelf so a group
 * looks the same wherever it appears. Sized for a card (a 19dp glyph read as a speck on a 104dp
 * tile) and tinted per category so a grid scans by colour as well as by shape.
 */
@Composable
internal fun GroupIconBadge(group: ChannelGroup, size: Dp = GroupBadgeSize) {
    val tone = groupTone(group)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(RadiusItem))
            .background(tone.copy(alpha = GROUP_BADGE_TONE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = groupIcon(group),
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(size * GROUP_GLYPH_FRACTION),
        )
    }
}

private val GroupBadgeSize = 44.dp
private const val GROUP_BADGE_TONE_ALPHA = 0.16f
private const val GROUP_GLYPH_FRACTION = 0.6f

/** Category hue for [groupIcon]; custom/provider groups keep the theme accent. */
@Composable
internal fun groupTone(group: ChannelGroup): Color {
    val hue = (group as? ChannelGroup.Known)?.let {
        when (it.key) {
            ChannelGroup.KEY_MOVIES -> CategoryTones.Hue.Rose
            ChannelGroup.KEY_NEWS -> CategoryTones.Hue.Sky
            ChannelGroup.KEY_SPORTS -> CategoryTones.Hue.Emerald
            ChannelGroup.KEY_KIDS -> CategoryTones.Hue.Amber
            ChannelGroup.KEY_MUSIC -> CategoryTones.Hue.Violet
            ChannelGroup.KEY_SERIES -> CategoryTones.Hue.Indigo
            ChannelGroup.KEY_DOCUMENTARY, ChannelGroup.KEY_SCIENCE -> CategoryTones.Hue.Teal
            ChannelGroup.KEY_ENTERTAINMENT -> CategoryTones.Hue.Fuchsia
            else -> null
        }
    } ?: return UaTheme.palette.azure
    return CategoryTones.color(hue, UaTheme.palette.isDark)
}

/** Presentation-only category cue; custom/provider groups keep a neutral channels glyph. */
internal fun groupIcon(group: ChannelGroup): ImageVector = when (group) {
    is ChannelGroup.Known -> when (group.key) {
        ChannelGroup.KEY_MOVIES -> AppIcons.Movie
        ChannelGroup.KEY_NEWS -> AppIcons.News
        ChannelGroup.KEY_SPORTS -> AppIcons.Sports
        ChannelGroup.KEY_SERIES,
        ChannelGroup.KEY_DOCUMENTARY,
        ChannelGroup.KEY_ENTERTAINMENT,
        -> AppIcons.Tv
        ChannelGroup.KEY_KIDS -> AppIcons.Kids
        ChannelGroup.KEY_MUSIC -> AppIcons.Music
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

    val shape = RoundedCornerShape(RadiusList)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .controlFeedback(pressed, PRESS_SCALE_ROUND)
            // Inside a LazyVerticalGrid (GroupsOverviewGrid) - shadow = false, see
            // docs/DESIGN_SYSTEM.md "§D Depth".
            .panelSurface(shape).tvFocus(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = groupLabel(grouped.group),
                onLongClickLabel = stringResource(R.string.channels_more_actions),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .heightIn(min = 104.dp)
            .padding(16.dp),
    ) {
        val label = groupLabel(grouped.group)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            GroupIconBadge(grouped.group)
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
            text = label,
            style = CardTitle,
            color = UaTheme.palette.labelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.channels_total_count, grouped.channels.size, grouped.channels.size),
            style = Caption,
            color = UaTheme.palette.labelSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
