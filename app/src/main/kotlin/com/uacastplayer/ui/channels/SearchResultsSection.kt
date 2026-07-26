package com.uacastplayer.ui.channels
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.playlist.ChannelListKeys
import com.uacastplayer.playlist.ChannelRowShape
import com.uacastplayer.playlist.ChannelSearchResult
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.HairlineInsetChannels
import com.uacastplayer.ui.theme.ItemPadding
import com.uacastplayer.ui.theme.RadiusList
import java.io.File

@Composable
internal fun NoSearchResults(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.channels_no_search_results, query),
            style = BodyText,
            color = UaTheme.palette.labelSecondary,
        )
    }
}

/** Flat, cross-group results for a whole-playlist [com.uacastplayer.playlist.ChannelSearch] - each
 * row shows which group a match came from, since it's no longer implied by an already-open group
 * screen. */
@Composable
internal fun ChannelSearchResultsList(
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
