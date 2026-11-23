package app.zorik.player.ui.channels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zorik.player.R
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.epg.CurrentNextProgrammes
import app.zorik.player.epg.ProgrammeProgress
import app.zorik.player.epg.EpgUiState
import app.zorik.player.epg.EpgLookup
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.NameQualityBadge
import app.zorik.player.ui.components.ChannelIcon
import app.zorik.player.ui.components.TrackProgress
import app.zorik.player.ui.components.controlFeedback
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.PRESS_SCALE_ROUND
import app.zorik.player.ui.theme.RadiusList
import app.zorik.player.ui.theme.UaTheme
import app.zorik.player.ui.theme.raisedSurface
import app.zorik.player.ui.tv.tvFocus
import java.io.File

internal const val LARGE_CATALOG_FONT_SCALE = 1.5f

@Composable
internal fun rememberChannelProgramme(channel: M3uChannel, state: EpgUiState): CurrentNextProgrammes? =
    remember(channel, state.data, state.nowMillis) {
        state.data?.let { EpgLookup.currentAndNext(it, channel, state.nowMillis) }
    }

/** Actions have their own 48dp bounds and never intercept the logo or the title. */
@Composable
internal fun ChannelQuickActions(
    channel: M3uChannel,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onGuide, modifier = Modifier.size(48.dp).tvFocus()) {
            Icon(AppIcons.Guide,
                contentDescription = stringResource(R.string.channels_channel_guide) + ": " + channel.displayName,
                tint = UaTheme.colors.material.primary)
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp).tvFocus()) {
            Icon(AppIcons.Favorites,
                contentDescription = stringResource(if (favorite) R.string.channels_channel_remove_favorite
                    else R.string.channels_channel_add_favorite),
                tint = if (favorite) UaTheme.colors.material.primary else UaTheme.palette.labelSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelRow(
    channel: M3uChannel,
    programme: CurrentNextProgrammes?,
    nowMillis: Long,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    density: ListDensity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isLocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onGuide: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val largeText = LocalDensity.current.fontScale >= LARGE_CATALOG_FONT_SCALE
    Column(modifier.fillMaxWidth().tvFocus().controlFeedback(pressed, PRESS_SCALE_ROUND)
        .combinedClickable(interactionSource = interactions, indication = null, role = Role.Button,
            onClickLabel = channel.displayName,
            onLongClickLabel = stringResource(R.string.channels_more_actions),
            onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (density != ListDensity.MINIMAL) {
                ChannelIcon(channel, resolveIcon, refreshKey = iconRefreshKey, size = 44.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(channel.displayName, style = BodyText, color = UaTheme.palette.labelPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (density == ListDensity.FULL) ChannelQualityBadge(channel)
                if (supportingText != null) Text(supportingText, style = Caption,
                    color = UaTheme.palette.labelSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (density == ListDensity.FULL) ChannelProgramme(programme, nowMillis)
            }
            if (isLocked) Icon(AppIcons.Lock, stringResource(R.string.channels_channel_locked),
                tint = UaTheme.palette.labelSecondary, modifier = Modifier.size(16.dp))
            if (!largeText) ChannelQuickActions(channel, isFavorite, onToggleFavorite, onGuide)
        }
        if (largeText) ChannelQuickActions(channel, isFavorite, onToggleFavorite, onGuide,
            Modifier.align(Alignment.End))
    }
}

@Composable
internal fun ChannelProgramme(programme: CurrentNextProgrammes?, nowMillis: Long) {
    val current = programme?.current ?: return
    Text(current.title, style = Caption, color = UaTheme.palette.labelSecondary,
        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    programme.effectiveStopMillis?.let { stop ->
        TrackProgress(ProgrammeProgress.progress(current.startMillis, stop, nowMillis),
            Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ChannelQualityBadge(channel: M3uChannel) {
    val quality = remember(channel.displayName) { NameQualityBadge.detect(channel.displayName) }
    quality?.let { Text(it, style = Caption, color = UaTheme.palette.accentText) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelTile(
    channel: M3uChannel,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    large: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    programme: CurrentNextProgrammes? = null,
    nowMillis: Long = 0L,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onGuide: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()
        .raisedSurface(RoundedCornerShape(RadiusList), UaTheme.palette.surface1,
            edgeColor = UaTheme.palette.hairline, shadow = false).tvFocus()
        .combinedClickable(role = Role.Button, onClickLabel = channel.displayName,
            onLongClickLabel = stringResource(R.string.channels_more_actions),
            onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            // The logo is the tile's subject: 44dp read as a small badge lost in a ~160dp card.
            ChannelIcon(channel, resolveIcon, size = if (large) GRID_LOGO_LARGE else GRID_LOGO,
                refreshKey = iconRefreshKey)
            if (isLocked) Icon(AppIcons.Lock, stringResource(R.string.channels_channel_locked),
                tint = UaTheme.palette.labelSecondary, modifier = Modifier.size(16.dp).align(Alignment.TopStart))
        }
        Text(channel.displayName, style = BodyText, color = UaTheme.palette.labelPrimary,
            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp))
        ChannelQualityBadge(channel)
        ChannelProgramme(programme, nowMillis)
        ChannelQuickActions(channel, isFavorite, onToggleFavorite, onGuide)
    }
}

private val GRID_LOGO = 72.dp
private val GRID_LOGO_LARGE = 96.dp
