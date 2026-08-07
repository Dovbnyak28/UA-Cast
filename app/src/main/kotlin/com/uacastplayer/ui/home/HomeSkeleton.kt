package com.uacastplayer.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.ui.components.SkeletonBlock
import com.uacastplayer.ui.components.SkeletonTextLine
import com.uacastplayer.ui.components.rememberShimmer
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.GapL
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.RadiusField
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.raisedSurface

/** Matches the real Button this stands in for. */
private val ButtonHeight = 48.dp

/**
 * What the Home tab shows while a playlist is loading for the first time.
 *
 * The branch this fills used to not exist: Home tested only `hasChannels`, so a cold start with a
 * cached playlist still restoring showed the "no playlist yet" empty state, complete with an "add a
 * playlist" button, for as long as the restore took - a screen actively telling the user the
 * opposite of the truth about their own data. `ChannelsScreen` already had the three-way branch
 * (channels win over loading, loading wins over empty); this is Home's half of it.
 *
 * Same deliberate duplication of proportions as [com.uacastplayer.ui.channels.GroupsSkeletonGrid],
 * and for the same reason - see its doc.
 */
@Composable
internal fun HomeDashboardSkeleton(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmer()

    // One node saying "loading" rather than a dozen decorative placeholders a screen reader would
    // walk through announcing nothing.
    val loadingLabel = stringResource(R.string.playlist_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = loadingLabel },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GapL)
                .raisedSurface(
                    RoundedCornerShape(RadiusCard),
                    UaTheme.palette.surface1,
                    edgeColor = UaTheme.palette.hairline,
                    shadow = true,
                )
                .padding(CardPadding),
        ) {
            SkeletonTextLine(shimmer = shimmer, widthFraction = 0.35f, height = 10.dp)
            SkeletonTextLine(
                shimmer = shimmer,
                widthFraction = 0.5f,
                height = 26.dp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = GapM)) {
                repeat(HOME_STAT_CELLS) {
                    SkeletonStatCell(shimmer = shimmer, modifier = Modifier.weight(1f))
                }
            }
            Column(modifier = Modifier.padding(top = GapM)) {
                SkeletonTextLine(shimmer = shimmer, widthFraction = 0.3f, height = 10.dp)
                SkeletonTextLine(
                    shimmer = shimmer,
                    widthFraction = 0.45f,
                    height = 16.dp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        SkeletonBlock(
            shimmer = shimmer,
            shape = RoundedCornerShape(RadiusField),
            modifier = Modifier.padding(top = GapL).fillMaxWidth().height(ButtonHeight),
        )
    }
}

/** The three counters on the dashboard card - channels, groups, favourites. */
private const val HOME_STAT_CELLS = 3

@Composable
private fun SkeletonStatCell(shimmer: State<Float>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SkeletonTextLine(shimmer = shimmer, widthFraction = 0.55f, height = 28.dp)
        SkeletonTextLine(
            shimmer = shimmer,
            widthFraction = 0.75f,
            height = 12.dp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
