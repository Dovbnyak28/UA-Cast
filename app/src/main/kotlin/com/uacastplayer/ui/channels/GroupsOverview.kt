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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.prefs.ChannelLayout
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
import com.uacastplayer.ui.theme.sunkenSurface
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

private val GroupBadgeSize = 44.dp

/** Inset into [GroupBadgeSize], so the mark sits in the plate rather than filling it. */
private val GroupLogoSize = 24.dp

/** One pixel at mdpi - the whole engraving. More reads as two glyphs, less as none. */
private val EngraveOffset = 1.dp

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
 * The app's own mark, engraved into the card.
 *
 * Two illustration schemes came before this and both were removed for the same reason: a *picture*
 * on a group card claims to say something about that group, and neither could. The first was a
 * collage of up to 4 channel logos pulled from whatever happened to be in the icon cache, so the
 * same group rendered differently on two launches. The second was a set of curated per-category
 * illustrations, which at least stayed still - but the category is already stated, in words,
 * directly beneath, so the picture repeated the title in a form carrying less information than the
 * title did.
 *
 * This one makes no claim at all, which is the point: it is the same neutral mark on every card, so
 * it reads as the card's material rather than as a statement about the group. Recessed rather than
 * raised for the same reason - [Modifier.sunkenSurface] makes the plate recede, and a mark that
 * recedes with it cannot compete with the group's name for attention.
 *
 * The engraving is the glyph drawn three times, and the order is what makes it read as cut rather
 * than printed: [UaPalette.void] one step *up* is the shadow the upper wall of a groove casts,
 * [UaPalette.edgeHighlightStrong] one step *down* is light catching its lower lip, and the body
 * over both is what the eye actually resolves as the mark.
 *
 * The body deliberately is **not** the darkest color available. Drawn in [UaPalette.void] on a
 * surface only slightly lighter than it, the first version of this was very nearly invisible on the
 * device - technically an engraving, practically an empty plate. [UaPalette.labelSecondary] keeps
 * it a foreground rather than a hole, and the two offset copies are what put it below the surface.
 */
@Composable
private fun EngravedAppMark() {
    Icon(
        imageVector = AppIcons.CastToTv,
        contentDescription = null,
        tint = UaTheme.palette.void,
        modifier = Modifier.size(GroupLogoSize).offset(y = -EngraveOffset),
    )
    Icon(
        imageVector = AppIcons.CastToTv,
        contentDescription = null,
        tint = UaTheme.palette.edgeHighlightStrong,
        modifier = Modifier.size(GroupLogoSize).offset(y = EngraveOffset),
    )
    Icon(
        imageVector = AppIcons.CastToTv,
        contentDescription = null,
        tint = UaTheme.palette.labelSecondary,
        modifier = Modifier.size(GroupLogoSize),
    )
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
        // One plate on every card, so a quality group and a plain one are the same height and the
        // grid's rows stay level.
        Box(
            modifier = Modifier
                .size(GroupBadgeSize)
                .sunkenSurface(RoundedCornerShape(RadiusItem), UaTheme.palette.surface2),
            contentAlignment = Alignment.Center,
        ) {
            val quality = groupQualityBadge(groupLabel(grouped.group))
            if (quality != null) {
                Text(
                    text = quality,
                    style = Caption,
                    color = UaTheme.palette.accentText,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                EngravedAppMark()
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
