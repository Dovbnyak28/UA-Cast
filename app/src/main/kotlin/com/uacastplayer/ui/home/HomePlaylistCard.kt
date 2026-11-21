package com.uacastplayer.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.theme.BodyText
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardTitle
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.SectionLabel
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.panelSurface

/**
 * The active-playlist card: which playlist is loaded, its three counters, and the EPG's state.
 *
 * Extracted from [HomeScreen] rather than left inline - the screen was at its complexity ceiling,
 * and this is the largest self-contained piece of it. [HomeDashboardSkeleton] mirrors these
 * proportions.
 */
@Composable
internal fun HomePlaylistCard(
    playlistState: PlaylistUiState,
    epgState: EpgUiState,
    totalChannels: Int,
    favoriteCount: Int,
    onRefreshPlaylist: () -> Unit,
    onChangeSource: () -> Unit,
    onOpenEpgSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryEpg: () -> Unit = {},
) {
    val expandedLabel = stringResource(R.string.home_less_details)
    val collapsedLabel = stringResource(R.string.home_more_details)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .panelSurface(RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_active_playlist_label), style = SectionLabel,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = UaTheme.palette.labelSecondary)
                Text(playlistState.displayName ?: stringResource(R.string.playlist_unnamed), style = CardTitle,
                    color = UaTheme.palette.labelPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onChangeSource, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.ChevronDown, stringResource(R.string.home_change_playlist), tint = UaTheme.palette.azure)
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp).semantics {
                contentDescription = if (expanded) expandedLabel else collapsedLabel
                stateDescription = if (expanded) expandedLabel else collapsedLabel
            }) { Icon(AppIcons.HelpCircle, null, tint = UaTheme.palette.labelSecondary) }
        }
        if (expanded) {
            if (playlistState.sourceUrl != null) {
                RefreshPlaylistButton(isLoading = playlistState.isLoading, onClick = onRefreshPlaylist)
            }
        Row(modifier = Modifier.fillMaxWidth().padding(top = GapM)) {
            HomeStatCell(
                count = totalChannels,
                label = pluralStringResource(R.plurals.home_stat_channels, totalChannels),
                modifier = Modifier.weight(1f),
            )
            HomeStatCell(
                count = playlistState.groups.size,
                label = pluralStringResource(R.plurals.home_stat_groups, playlistState.groups.size),
                modifier = Modifier.weight(1f),
            )
            HomeStatCell(
                count = favoriteCount,
                label = pluralStringResource(R.plurals.home_stat_favorites, favoriteCount),
                modifier = Modifier.weight(1f),
            )
        }

            if (playlistState.restoredFromCache) {
                Text(
                    text = stringResource(R.string.home_restored_from_cache),
                    style = Caption,
                    color = UaTheme.palette.labelSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }


            Column(modifier = Modifier.padding(top = GapM)) {
                Text(
                    text = stringResource(R.string.home_epg_label),
                    style = SectionLabel,
                    color = UaTheme.palette.labelSecondary,
                )
                Text(
                    text = stringResource(homeEpgStatus(epgState)),
                    style = BodyText,
                    color = UaTheme.palette.labelPrimary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            HomeEpgActions(epgState, onOpenEpgSettings, onRetryEpg)
        }
    }
}

/** Re-fetches the active playlist from its saved URL - see [PlaylistUiState.sourceUrl]. Swaps to a
 * spinner instead of disabling outright while a load is already in flight, so the user can see
 * their tap registered rather than wondering if the button is just unresponsive. */
@Composable
private fun RefreshPlaylistButton(isLoading: Boolean, onClick: () -> Unit) {
    // The label sits on the button rather than on the icon, because the icon is only there in one of
    // the two states: while loading it is a spinner, and a spinner carries no description - which
    // left this control announcing itself to TalkBack as an unnamed disabled button for exactly as
    // long as the refresh it was reporting.
    val label = stringResource(R.string.home_refresh_playlist)
    IconButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = UaTheme.palette.azure,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(AppIcons.Refresh, contentDescription = null, tint = UaTheme.palette.azure)
        }
    }
}

@Composable
private fun HomeStatCell(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(text = count.toString(), style = Title, color = UaTheme.palette.labelPrimary)
        Text(
            text = label, style = Caption, color = UaTheme.palette.labelSecondary,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
        )
    }
}


@Composable
private fun HomeEpgActions(state: EpgUiState, onConfigure: () -> Unit, onRetry: () -> Unit) {
    if (state.hasError && !state.customUrl.isNullOrBlank()) {
        TextButton(onClick = onRetry, enabled = !state.isLoading) {
            Text(stringResource(R.string.common_retry))
        }
    }
    TextButton(onClick = onConfigure) { Text(stringResource(R.string.home_epg_configure)) }
}
