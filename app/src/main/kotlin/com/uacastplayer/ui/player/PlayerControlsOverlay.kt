package com.uacastplayer.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.uacastplayer.R
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.SleepTimerFormatter
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BREATHE_MS
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.LiveText
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme

private const val LIVE_LABEL_MIN_ALPHA = 0.6f
private const val TOP_SCRIM_END = 0.22f
private const val BOTTOM_SCRIM_START = 0.70f

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
@Suppress("LongParameterList") // Stateless overlay callbacks; platform level controls live in PlayerLevelsSheet.
internal fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    isFullscreen: Boolean,
    // State, not a plain Long? - see SleepTimerState's doc. Kept unread until SleepTimerButton's
    // own body so the once-a-second tick only recomposes that small pill, not this whole overlay.
    sleepTimerRemainingMillis: State<Long?>,
    onExit: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    isDlnaCasting: Boolean,
    onOpenDlnaSheet: () -> Unit,
    onSelectPreview: (IndexedChannel) -> Unit,
    onOpenActions: () -> Unit = {},
    fileProgress: (@Composable () -> Unit)? = null,
) {
    val isFile = uiState.currentChannel?.localVideo != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Keep controls readable over bright live video without dimming the centre of the
            // picture. The scrim is theme-owned because it is an overlay tone, not app chrome.
            .background(
                Brush.verticalGradient(
                    0f to UaTheme.palette.scrimBackground.copy(alpha = 0.90f),
                    TOP_SCRIM_END to Color.Transparent,
                    BOTTOM_SCRIM_START to Color.Transparent,
                    1f to UaTheme.palette.scrimBackground.copy(alpha = 0.94f),
                ),
            ),
    ) {
        PlayerOverlayHeader(uiState, onExit, isDlnaCasting, onOpenDlnaSheet, onOpenActions)

        Box(modifier = Modifier.weight(1f))

        if (uiState.nextChannelsPreview.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = ScreenHPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.nextChannelsPreview, key = { it.index }) { indexed ->
                    Text(
                        text = indexed.channel.displayName,
                        color = UaTheme.palette.labelPrimary,
                        style = Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .clip(RoundedCornerShape(RadiusItem))
                            .background(UaTheme.palette.scrimBackground)
                            .border(1.dp, UaTheme.palette.overlayHighlight, RoundedCornerShape(RadiusItem))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = indexed.channel.displayName,
                                onClick = { onSelectPreview(indexed) },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (isFile) fileProgress?.invoke()
        PlayerOverlayFooter(uiState, isFullscreen, sleepTimerRemainingMillis,
            PlayerOverlayFooterActions(
                onPlayPause, onNext, onPrevious, onToggleFullscreen, onEnterPip, onOpenSleepTimer,
            ))
    }
}

/**
 * Opens the sleep timer dialog. Shows a plain icon when idle; once a timer is running, widens into
 * a pill showing the live countdown instead, so the remaining time is visible without opening the
 * dialog.
 */
@Composable
internal fun SleepTimerButton(remainingMillis: State<Long?>, onClick: () -> Unit) {
    val remaining = remainingMillis.value
    if (remaining == null) {
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
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.player_sleep_timer),
                    onClick = onClick,
                )
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
                text = SleepTimerFormatter.formatRemaining(remaining),
                color = UaTheme.palette.labelPrimary,
                style = Caption,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** §4 rule 3 - LIVE dot breathing between alpha/scale 1 and 0.3/0.8, reversed, on an infinite loop. */
@Composable
internal fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "liveBreathe")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(BREATHE_MS), repeatMode = RepeatMode.Reverse),
        label = "liveAlpha",
    )
    val dotScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(BREATHE_MS), repeatMode = RepeatMode.Reverse),
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
            color = UaTheme.palette.routeRed.copy(alpha = alpha.coerceAtLeast(LIVE_LABEL_MIN_ALPHA)),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
