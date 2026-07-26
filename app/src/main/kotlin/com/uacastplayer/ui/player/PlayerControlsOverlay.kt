package com.uacastplayer.ui.player
import com.uacastplayer.ui.theme.UaTheme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.uacastplayer.R
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.SleepTimerFormatter
import com.uacastplayer.ui.components.GradientPlayButton
import com.uacastplayer.ui.components.RoundIconButton
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BreatheMs
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.DisplayName
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.LiveText
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.ScreenHPadding

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    isFullscreen: Boolean,
    sleepTimerRemainingMillis: Long?,
    onExit: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSelectPreview: (IndexedChannel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallRoundIconButton(
                icon = AppIcons.ArrowBack,
                onClick = onExit,
                contentDescription = stringResource(R.string.common_back),
                background = UaTheme.palette.scrimBackground,
            )
            Column(modifier = Modifier.padding(start = GapM).weight(1f)) {
                Text(text = uiState.currentChannel?.displayName.orEmpty(), color = Color.White, style = DisplayName)
                BadgesRow(uiState.badges)
            }
            LiveIndicator()
            PlayerCastButton(modifier = Modifier.padding(start = GapM))
        }

        Box(modifier = Modifier.weight(1f))

        if (uiState.nextChannelsPreview.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.nextChannelsPreview, key = { it.index }) { indexed ->
                    Text(
                        text = indexed.channel.displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(RadiusItem))
                            .background(UaTheme.palette.scrimBackground)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .pointerInput(indexed.index) {
                                detectTapGestures { onSelectPreview(indexed) }
                            },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                icon = AppIcons.SkipPrevious,
                onClick = onPrevious,
                contentDescription = stringResource(R.string.player_previous),
            )
            GradientPlayButton(
                icon = if (uiState.isPlaying) AppIcons.Pause else AppIcons.Play,
                onClick = onPlayPause,
                contentDescription = playPauseLabel(uiState.isPlaying),
            )
            RoundIconButton(
                icon = AppIcons.SkipNext,
                onClick = onNext,
                contentDescription = stringResource(R.string.player_next),
            )
            Box(modifier = Modifier.weight(1f))
            SleepTimerButton(remainingMillis = sleepTimerRemainingMillis, onClick = onOpenSleepTimer)
            SmallRoundIconButton(
                icon = AppIcons.PictureInPicture,
                onClick = onEnterPip,
                contentDescription = stringResource(R.string.player_picture_in_picture),
                background = UaTheme.palette.scrimBackground,
            )
            SmallRoundIconButton(
                icon = if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                onClick = onToggleFullscreen,
                contentDescription = stringResource(
                    if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen
                ),
                background = UaTheme.palette.scrimBackground,
            )
        }
    }
}

/**
 * Opens the sleep timer dialog. Shows a plain icon when idle; once a timer is running, widens into
 * a pill showing the live countdown instead, so the remaining time is visible without opening the
 * dialog.
 */
@Composable
private fun SleepTimerButton(remainingMillis: Long?, onClick: () -> Unit) {
    if (remainingMillis == null) {
        SmallRoundIconButton(
            icon = AppIcons.Timer,
            onClick = onClick,
            contentDescription = stringResource(R.string.player_sleep_timer),
            background = UaTheme.palette.scrimBackground,
        )
    } else {
        Row(
            modifier = Modifier
                .height(IconButtonSize)
                .clip(RoundedCornerShape(IconButtonSize / 2))
                .background(UaTheme.palette.scrimBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Timer,
                contentDescription = stringResource(R.string.player_sleep_timer),
                tint = UaTheme.palette.azure,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = SleepTimerFormatter.formatRemaining(remainingMillis),
                color = Color.White,
                style = Caption,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** §4 rule 3 - LIVE dot breathing between alpha/scale 1 and 0.3/0.8, reversed, on an infinite loop. */
@Composable
private fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "liveBreathe")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(BreatheMs), repeatMode = RepeatMode.Reverse),
        label = "liveAlpha",
    )
    val dotScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(BreatheMs), repeatMode = RepeatMode.Reverse),
        label = "liveScale",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .scale(dotScale)
                .background(UaTheme.palette.routeRed.copy(alpha = alpha), CircleShape)
                .background(UaTheme.palette.redGlow.copy(alpha = alpha * 0.4f), CircleShape),
        )
        Text(
            text = stringResource(R.string.player_live_indicator),
            style = LiveText,
            color = UaTheme.palette.routeRed.copy(alpha = alpha.coerceAtLeast(0.6f)),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
