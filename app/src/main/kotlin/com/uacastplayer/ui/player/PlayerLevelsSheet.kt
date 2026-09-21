package com.uacastplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uacastplayer.R
import com.uacastplayer.player.PlayerGesturePolicy
import com.uacastplayer.ui.components.SmallRoundIconButton
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.Caption
import com.uacastplayer.ui.theme.ScreenHPadding
import com.uacastplayer.ui.theme.Title
import com.uacastplayer.ui.theme.UaTheme
import kotlin.math.roundToInt

/** Accessible alternatives to video gestures, reachable in portrait and fullscreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerLevelsSheet(state: PlayerScreenTransientState, environment: PlayerScreenEnvironment) {
    LaunchedEffect(state, environment.audioManager) {
        state.volumeLevel = environment.audioManager.currentVolumeFraction()
    }
    ModalBottomSheet(
        onDismissRequest = { state.showLevelsSheet = false },
        containerColor = UaTheme.palette.surface2,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenHPadding)) {
            Text(stringResource(R.string.player_levels), style = Title)
            LevelStepperRow(
                brightnessLevel = state.brightnessLevel,
                volumeLevel = state.volumeLevel,
                onBrightnessStep = { delta ->
                    state.brightnessLevel = PlayerGesturePolicy.applyLevelDelta(state.brightnessLevel, delta)
                    environment.activity?.let { applyWindowBrightness(it, state.brightnessLevel) }
                },
                onVolumeStep = { delta ->
                    environment.audioManager?.let { state.volumeLevel = stepStreamVolume(it, increase = delta > 0f) }
                },
            )
        }
    }
}

private const val LEVEL_STEP = 0.1f
private const val PERCENT_SCALE = 100

/** Stepper pair for brightness (left) and volume (right), see the call site's comment. */
@Composable
internal fun LevelStepperRow(
    brightnessLevel: Float,
    volumeLevel: Float,
    onBrightnessStep: (Float) -> Unit,
    onVolumeStep: (Float) -> Unit,
) {
    val brightnessPercent = (brightnessLevel * PERCENT_SCALE).roundToInt()
    val volumePercent = (volumeLevel * PERCENT_SCALE).roundToInt()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
