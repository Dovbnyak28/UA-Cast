package com.uacastplayer.ui.player
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.core.settings.PlayerResizeMode
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlaybackBadgesState
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.ResizeModeCycle
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.ui.components.ChannelIcon
import com.uacastplayer.ui.components.artworkWash
import com.uacastplayer.ui.components.rememberArtworkTone
import com.uacastplayer.ui.components.GradientPlayButton
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.CardPadding
import com.uacastplayer.ui.theme.DisplayName
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.QuickSettingLabelHeight
import com.uacastplayer.ui.theme.RadiusCard
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.raisedSurface
import java.io.File

/** The non-fullscreen player's "info & quick actions" furniture, below the inline video: the
 * previous/next pill row, current-channel card, quick-settings row and next-channels rail, plus
 * the cast-incompatibility banner shown above them when relevant. Grouped together since they're
 * only ever composed as this one vertical stack in [PlayerScreen] and share no state with the
 * fullscreen [PlayerControlsOverlay]. */
@Composable
internal fun PillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTrailing: Boolean = false,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .raisedSurface(RoundedCornerShape(RadiusCard), UaTheme.palette.surface1, shadow = false)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconTrailing) {
            Icon(
                icon,
                contentDescription = null,
                tint = UaTheme.palette.labelPrimary,
                modifier = Modifier.size(18.dp).padding(end = 8.dp),
            )
        }
        Text(text = label, style = Caption, color = UaTheme.palette.labelPrimary)
        if (iconTrailing) {
            Icon(
                icon,
                contentDescription = null,
                tint = UaTheme.palette.labelPrimary,
                modifier = Modifier.size(18.dp).padding(start = 8.dp),
            )
        }
    }
}

/** Shown while casting has some reason it isn't reaching the receiver - either a codec
 * [com.uacastplayer.core.cast.CastCompatibilityPolicy] flagged as incompatible, or the receiver
 * rejecting/erroring on the proxy fallback for any other reason. Local playback keeps playing
 * regardless, this only explains why the receiver isn't. Clears itself once the relevant
 * [PlayerUiState] field goes back to its default (new channel, cast disconnect, or - for a codec
 * incompatibility - the receiver recovering). */
@Composable
internal fun CastIncompatibilityBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .raisedSurface(RoundedCornerShape(RadiusCard), UaTheme.palette.surface1, shadow = false)
            .padding(horizontal = CardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AppIcons.HelpCircle,
            contentDescription = null,
            tint = UaTheme.palette.routeAmber,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = Caption,
            color = UaTheme.palette.labelPrimary,
            modifier = Modifier.padding(start = GapM),
        )
    }
}

@Composable
internal fun ChannelInfoCard(
    channel: M3uChannel,
    badges: PlaybackBadgesState,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
) {
    // Borrowed from the logo about to be drawn two lines below, so the card belongs to the channel
    // playing rather than being the same grey box for all 2863 of them. Skipped entirely on a theme
    // that paints no wallpaper texture: Midnight is true black on purpose, and any wash at all is
    // the one thing it exists not to have.
    val artworkTone = rememberArtworkTone(channel = channel, resolveIcon = resolveIcon)
        .takeIf { UaTheme.palette.wallpaperTexture }
    val cardShape = RoundedCornerShape(RadiusCard)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHPadding)
            .raisedSurface(
                cardShape,
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = true,
            )
            .artworkWash(tone = artworkTone, shape = cardShape)
            .padding(CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelIcon(channel = channel, resolveIcon = resolveIcon, size = 64.dp, refreshKey = iconRefreshKey)
        Column(modifier = Modifier.padding(start = GapM).weight(1f)) {
            Text(text = channel.displayName, style = DisplayName, color = UaTheme.palette.labelPrimary, maxLines = 1)
            BadgesRow(badges, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun QuickSettingsRow(
    onAudioClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onQualityClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onGuideClick: () -> Unit,
    onPreviousChannelClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHPadding, vertical = GapM)
            .raisedSurface(
                RoundedCornerShape(RadiusCard),
                UaTheme.palette.surface1,
                edgeColor = UaTheme.palette.hairline,
                shadow = true,
            )
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Only shown once there's actually somewhere to jump back to - see
        // PlayerUiState.hasPreviousChannel. Each item gets an equal weight() share of the row so a
        // 6th item (this one) doesn't squeeze the others' labels into character-by-character wrap -
        // see docs/DESIGN_SYSTEM.md "§E Equal-share rows".
        onPreviousChannelClick?.let {
            QuickSettingItem(
                AppIcons.Refresh,
                stringResource(R.string.player_previous_channel),
                it,
                modifier = Modifier.weight(1f),
            )
        }
        QuickSettingItem(
            AppIcons.AudioTrack,
            stringResource(R.string.player_audio_track),
            onAudioClick,
            modifier = Modifier.weight(1f),
        )
        QuickSettingItem(
            AppIcons.Subtitles,
            stringResource(R.string.player_subtitle_track),
            onSubtitlesClick,
            modifier = Modifier.weight(1f),
        )
        QuickSettingItem(
            AppIcons.Quality,
            stringResource(R.string.player_quality),
            onQualityClick,
            modifier = Modifier.weight(1f),
        )
        QuickSettingItem(
            AppIcons.AspectRatio,
            stringResource(R.string.player_aspect_ratio),
            onAspectRatioClick,
            modifier = Modifier.weight(1f),
        )
        QuickSettingItem(
            AppIcons.Guide,
            stringResource(R.string.player_tv_guide),
            onGuideClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.QuickSettingItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .raisedSurface(RoundedCornerShape(12.dp), UaTheme.palette.surface2, shadow = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = UaTheme.palette.azure, modifier = Modifier.size(18.dp))
        }
        Box(
            // A *minimum*, not a fixed height. Fixed, this reserved 32dp less the 6dp of top
            // padding - 26dp - while two lines of 12sp Caption need about 29dp, so the second line
            // was drawn into space that did not exist and its descenders were cut off: the
            // Ukrainian "Формат кадру" lost the tail of its "у". The point of the constant is to
            // keep one-line labels reserving the same space so the icons above them stay aligned,
            // and a minimum does that just as well while letting a two-line label have the room it
            // actually needs. Every item is top-aligned in its Column, so the icons stay in line
            // even when one label is taller than the rest.
            modifier = Modifier.heightIn(min = QuickSettingLabelHeight).padding(top = 6.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = label,
                style = Caption,
                color = UaTheme.palette.labelSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
internal fun NextChannelsRail(
    channels: List<IndexedChannel>,
    iconRefreshKey: Any,
    resolveIcon: suspend (M3uChannel) -> File?,
    onSelect: (IndexedChannel) -> Unit,
) {
    Column(modifier = Modifier.padding(top = GapM)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.player_next_channels_title),
                style = Title,
                color = UaTheme.palette.labelPrimary,
            )
            Text(text = stringResource(R.string.player_view_all), style = Caption, color = UaTheme.palette.accentText)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = GapM, start = ScreenHPadding, end = ScreenHPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(channels, key = { it.index }) { indexed ->
                Column(
                    // Inside a LazyRow - shadow = false, see docs/DESIGN_SYSTEM.md "§D Depth".
                    modifier = Modifier
                        .raisedSurface(RoundedCornerShape(RadiusCard), UaTheme.palette.surface1, shadow = false)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = indexed.channel.displayName,
                            onClick = { onSelect(indexed) },
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChannelIcon(
                        channel = indexed.channel,
                        resolveIcon = resolveIcon,
                        size = 64.dp,
                        refreshKey = iconRefreshKey,
                    )
                    Text(
                        text = indexed.channel.displayName,
                        style = Caption,
                        color = UaTheme.palette.labelPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp).width(96.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Transient pill shown after cycling the aspect ratio - same visual language as the buffering
 * pill, just centered instead of anchored to a corner since it isn't tied to a screen edge the
 * way the fullscreen brightness/volume bars are. */
@Composable
internal fun ResizeModeToast(mode: PlayerResizeMode, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(UaTheme.palette.scrimBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.AspectRatio, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(ResizeModeCycle.labelRes(mode)),
            color = Color.White,
            style = Caption,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
internal fun InlineVideoControls(isPlaying: Boolean, onPlayPause: () -> Unit, onToggleFullscreen: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        GradientPlayButton(
            icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
            onClick = onPlayPause,
            contentDescription = playPauseLabel(isPlaying),
            modifier = Modifier.align(Alignment.Center),
        )
        SmallRoundIconButton(
            icon = AppIcons.Fullscreen,
            onClick = onToggleFullscreen,
            contentDescription = stringResource(R.string.player_fullscreen),
            background = UaTheme.palette.scrimBackground,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        )
    }
}
