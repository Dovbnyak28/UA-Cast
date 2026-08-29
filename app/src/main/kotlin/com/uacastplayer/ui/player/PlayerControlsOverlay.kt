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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.uacastplayer.R
import com.uacastplayer.player.IndexedChannel
import com.uacastplayer.player.PlayerUiState
import com.uacastplayer.player.SleepTimerFormatter
import com.uacastplayer.ui.components.GradientPlayButton
import com.uacastplayer.ui.components.RoundIconButton
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.components.liveRing
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.BREATHE_MS
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.DisplayName
import com.uacastplayer.ui.theme.GapM
import com.uacastplayer.ui.theme.IconButtonSize
import com.uacastplayer.ui.theme.LiveText
import com.uacastplayer.ui.theme.RadiusItem
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.UaTheme
import kotlin.math.roundToInt

private const val LIVE_LABEL_MIN_ALPHA = 0.6f
private const val TOP_SCRIM_END = 0.22f
private const val BOTTOM_SCRIM_START = 0.70f

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
@Suppress("LongParameterList") // one accessible stepper pair added for brightness/volume, see below
internal fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    isFullscreen: Boolean,
    // State, not a plain Long? - see SleepTimerState's doc. Kept unread until SleepTimerButton's
    // own body so the once-a-second tick only recomposes that small pill, not this whole overlay.
    sleepTimerRemainingMillis: State<Long?>,
    brightnessLevel: Float,
    volumeLevel: Float,
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
    onBrightnessStep: (Float) -> Unit,
    onVolumeStep: (Float) -> Unit,
) {
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 12.dp),
            // One shared token for every gap in this row (including the one between the channel
            // name and the live dot, which previously had none at all) instead of each item
            // carrying its own ad-hoc start padding - see PlayerCastButton's matching raisedSurface
            // fix for the other half of why the cast button used to look out of place here.
            horizontalArrangement = Arrangement.spacedBy(GapM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallRoundIconButton(
                icon = AppIcons.ArrowBack,
                onClick = onExit,
                contentDescription = stringResource(R.string.common_back),
                background = UaTheme.palette.scrimBackground,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.currentChannel?.displayName.orEmpty(),
                    color = UaTheme.palette.labelPrimary,
                    style = DisplayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BadgesRow(uiState.badges)
            }
            LiveIndicator()
            // Sits beside the Cast button rather than replacing it: the two reach different
            // hardware (Cast for Google devices, DLNA for the Samsung/LG/Sony sets with no Cast
            // receiver), so a user with either kind needs to see the one that applies to them.
            SmallRoundIconButton(
                icon = AppIcons.CastToTv,
                onClick = onOpenDlnaSheet,
                contentDescription = stringResource(R.string.player_dlna_cast),
                background = UaTheme.palette.scrimBackground,
                tint = if (isDlnaCasting) UaTheme.palette.azure else UaTheme.palette.labelPrimary,
                modifier = Modifier.liveRing(active = isDlnaCasting, color = UaTheme.palette.azure),
            )
            PlayerCastButton(isCasting = uiState.isCasting)
        }

        // TalkBack-reachable alternative to the fullscreen brightness/volume drag gesture (see
        // PlayerScreen's pointerInput(activity, audioManager) block) - the drag zones have no
        // other way for a screen-reader user to reach them, so these steppers are the accessible
        // equivalent, not just a visual convenience.
        LevelStepperRow(
            brightnessLevel = brightnessLevel,
            volumeLevel = volumeLevel,
            onBrightnessStep = onBrightnessStep,
            onVolumeStep = onVolumeStep,
        )

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
                            .clickable { onSelectPreview(indexed) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
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

private const val LEVEL_STEP = 0.1f
private const val PERCENT_SCALE = 100

/** Stepper pair for brightness (left) and volume (right), see the call site's comment. */
@Composable
private fun LevelStepperRow(
    brightnessLevel: Float,
    volumeLevel: Float,
    onBrightnessStep: (Float) -> Unit,
    onVolumeStep: (Float) -> Unit,
) {
    val brightnessPercent = (brightnessLevel * PERCENT_SCALE).roundToInt()
    val volumePercent = (volumeLevel * PERCENT_SCALE).roundToInt()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelStepper(
            contextIcon = AppIcons.Brightness,
            percent = brightnessPercent,
            decreaseDescription = stringResource(R.string.player_brightness_decrease, brightnessPercent),
            increaseDescription = stringResource(R.string.player_brightness_increase, brightnessPercent),
            onDecrease = { onBrightnessStep(-LEVEL_STEP) },
            onIncrease = { onBrightnessStep(LEVEL_STEP) },
        )
        LevelStepper(
            contextIcon = AppIcons.Volume,
            percent = volumePercent,
            decreaseDescription = stringResource(R.string.player_volume_decrease, volumePercent),
            increaseDescription = stringResource(R.string.player_volume_increase, volumePercent),
            onDecrease = { onVolumeStep(-LEVEL_STEP) },
            onIncrease = { onVolumeStep(LEVEL_STEP) },
        )
    }
}

/** A visually self-describing alternative to the player's brightness/volume gestures. */
@Composable
private fun LevelStepper(
    contextIcon: ImageVector,
    percent: Int,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(UaTheme.palette.scrimBackground)
            .border(1.dp, UaTheme.palette.overlayHighlight, RoundedCornerShape(999.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = contextIcon,
            contentDescription = null,
            tint = UaTheme.palette.labelPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "$percent%",
            color = UaTheme.palette.labelPrimary,
            style = Caption,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 38.dp),
        )
        SmallRoundIconButton(
            icon = AppIcons.Minus,
            onClick = onDecrease,
            contentDescription = decreaseDescription,
            background = Color.Transparent,
        )
        SmallRoundIconButton(
            icon = AppIcons.Plus,
            onClick = onIncrease,
            contentDescription = increaseDescription,
            background = Color.Transparent,
        )
    }
}

/**
 * Opens the sleep timer dialog. Shows a plain icon when idle; once a timer is running, widens into
 * a pill showing the live countdown instead, so the remaining time is visible without opening the
 * dialog.
 */
@Composable
private fun SleepTimerButton(remainingMillis: State<Long?>, onClick: () -> Unit) {
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
private fun LiveIndicator() {
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
