package com.uacastplayer.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.data.prefs.ChannelLayout
import androidx.compose.runtime.State
import com.uacastplayer.ui.components.SkeletonBadge
import com.uacastplayer.ui.components.SkeletonBlock
import com.uacastplayer.ui.components.SkeletonTextLine
import com.uacastplayer.ui.components.rememberShimmer
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.GroupTileMinWidth
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.RadiusList
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.raisedSurface

/** Enough cards to fill a phone screen in either layout; the real grid replaces them before a user
 * could scroll far enough to run out. */
private const val SKELETON_CARD_COUNT = 8

/** Matches the real OutlinedTextField this stands in for. */
private val SearchFieldHeight = 56.dp

/**
 * What the Channels tab shows while a playlist is loading for the first time.
 *
 * Deliberately a copy of the real layout's proportions rather than a shared composable with the
 * live one: a skeleton has no data, so factoring the two together would mean threading nullable
 * everything through [GroupsOverviewGrid] for the benefit of a screen that exists for two seconds.
 * The cost of the copy is that the two can drift - which is survivable, because a skeleton that no
 * longer matches exactly still does its job (it holds the space and shows the shape), whereas a
 * `GroupedChannels?` in the live grid would be a permanent tax on the code that matters.
 */
@Composable
internal fun GroupsSkeletonGrid(layout: ChannelLayout, modifier: Modifier = Modifier) {
    val shimmer = rememberShimmer()
    val gridCells = if (layout == ChannelLayout.LIST) GridCells.Fixed(1) else GridCells.Adaptive(GroupTileMinWidth)

    // The whole skeleton is one node saying "loading", replacing the text this took the place of.
    // Without clearAndSetSemantics a screen reader walks a dozen decorative placeholders that say
    // nothing, which is strictly worse than the spinner-plus-label it replaced.
    val loadingLabel = stringResource(R.string.playlist_loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = loadingLabel }
            .padding(top = GapM),
    ) {
        SkeletonTextLine(shimmer = shimmer, widthFraction = 0.45f, height = 28.dp)
        SkeletonTextLine(
            shimmer = shimmer,
            widthFraction = 0.3f,
            height = 14.dp,
            modifier = Modifier.padding(top = 8.dp),
        )
        SkeletonBlock(
            shimmer = shimmer,
            shape = RoundedCornerShape(RadiusField),
            modifier = Modifier.padding(top = GapM).fillMaxWidth().height(SearchFieldHeight),
        )
        LazyVerticalGrid(
            columns = gridCells,
            modifier = Modifier.fillMaxSize().padding(top = GapM),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items((0 until SKELETON_CARD_COUNT).toList()) {
                SkeletonGroupCard(shimmer = shimmer)
            }
        }
    }
}

@Composable
private fun SkeletonGroupCard(shimmer: State<Float>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .raisedSurface(
                RoundedCornerShape(RadiusList),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = false,
            )
            .padding(16.dp),
    ) {
        SkeletonBadge(shimmer = shimmer, size = 44.dp, shape = RoundedCornerShape(RadiusItem))
        SkeletonTextLine(
            shimmer = shimmer,
            widthFraction = 0.25f,
            height = 3.dp,
            modifier = Modifier.padding(top = 12.dp),
        )
        SkeletonTextLine(
            shimmer = shimmer,
            widthFraction = 0.7f,
            height = 16.dp,
            modifier = Modifier.padding(top = 10.dp),
        )
        SkeletonTextLine(
            shimmer = shimmer,
            widthFraction = 0.45f,
            height = 12.dp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
