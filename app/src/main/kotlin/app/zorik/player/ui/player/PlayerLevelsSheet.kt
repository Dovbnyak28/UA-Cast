package app.zorik.player.ui.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.zorik.player.R
import app.zorik.player.player.PlayerGesturePolicy
import app.zorik.player.ui.components.SmallRoundIconButton
import app.zorik.player.ui.theme.AppIcons
import app.zorik.player.ui.theme.Caption
import app.zorik.player.ui.theme.ScreenHPadding
import app.zorik.player.ui.theme.BodyText
import app.zorik.player.ui.theme.panelSurface
import app.zorik.player.ui.theme.Title
import app.zorik.player.ui.theme.UaTheme
import kotlin.math.roundToInt

/** Accessible alternatives to video gestures, reachable in portrait and fullscreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerLevelsSheet(state: PlayerScreenTransientState, environment: PlayerScreenEnvironment) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, environment.audioManager, environment.activity, lifecycle) {
        observeResumedPlayerLevels(lifecycle) {
            state.volumeLevel = environment.audioManager.currentVolumeFraction()
            environment.activity?.let { state.brightnessLevel = initialBrightnessLevel(it) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = { state.showLevelsSheet = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = UaTheme.palette.surface2,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenHPadding)) {
            Text(stringResource(R.string.player_levels), style = Title)
            PlayerLevelControls(
                brightnessLevel = state.brightnessLevel,
                volumeLevel = state.volumeLevel,
                onBrightnessStep = { delta ->
                    val current = environment.activity?.let(::initialBrightnessLevel) ?: state.brightnessLevel
                    state.brightnessLevel = PlayerGesturePolicy.applyLevelDelta(current, delta)
                    environment.activity?.let { applyWindowBrightness(it, state.brightnessLevel) }
                },
                onVolumeStep = { delta ->
                    environment.audioManager?.let { state.volumeLevel = stepStreamVolume(it, increase = delta > 0f) }
                },
                onBrightnessValue = { value ->
                    state.brightnessLevel = value
                    environment.activity?.let { applyWindowBrightness(it, value) }
                },
                onVolumeValue = { value ->
                    environment.audioManager?.let { audio ->
                        applyStreamVolume(audio, value)
                        state.volumeLevel = audio.currentVolumeFraction()
                    }
                },
            )
        }
    }
}

private const val LEVEL_STEP = 0.1f
private const val PERCENT_SCALE = 100

/** Full-width labelled levels, with both direct adjustment and precise accessible steps. */
@Composable
internal fun PlayerLevelControls(
    brightnessLevel: Float,
    volumeLevel: Float,
    onBrightnessStep: (Float) -> Unit,
    onVolumeStep: (Float) -> Unit,
    onBrightnessValue: (Float) -> Unit,
    onVolumeValue: (Float) -> Unit,
) {
    val brightnessPercent = (brightnessLevel * PERCENT_SCALE).roundToInt()
    val volumePercent = (volumeLevel * PERCENT_SCALE).roundToInt()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LevelControlCard(
            contextIcon = AppIcons.Brightness,
            label = stringResource(R.string.player_brightness),
            level = brightnessLevel,
            onValueChange = onBrightnessValue,
            sliderTag = "player_brightness_slider",
            percent = brightnessPercent,
            decreaseDescription = stringResource(R.string.player_brightness_decrease, brightnessPercent),
            increaseDescription = stringResource(R.string.player_brightness_increase, brightnessPercent),
            onDecrease = { onBrightnessStep(-LEVEL_STEP) },
            onIncrease = { onBrightnessStep(LEVEL_STEP) },
        )
        LevelControlCard(
            contextIcon = AppIcons.Volume,
            label = stringResource(R.string.player_volume),
            level = volumeLevel,
            onValueChange = onVolumeValue,
            sliderTag = "player_volume_slider",
            percent = volumePercent,
            decreaseDescription = stringResource(R.string.player_volume_decrease, volumePercent),
            increaseDescription = stringResource(R.string.player_volume_increase, volumePercent),
            onDecrease = { onVolumeStep(-LEVEL_STEP) },
            onIncrease = { onVolumeStep(LEVEL_STEP) },
        )
    }
}

/** The slider reads the same confirmed state as the percentage; a refused volume write cannot fake progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelControlCard(
    contextIcon: ImageVector,
    label: String,
    level: Float,
    percent: Int,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onValueChange: (Float) -> Unit,
    sliderTag: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val sliderColors = SliderDefaults.colors(thumbColor = UaTheme.palette.azure,
        activeTrackColor = UaTheme.palette.azure, inactiveTrackColor = UaTheme.palette.surface2)
    Column(Modifier.fillMaxWidth().panelSurface(RoundedCornerShape(20.dp)).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(contextIcon, null, tint = UaTheme.palette.azure, modifier = Modifier.size(22.dp))
            Text(label, style = BodyText, color = UaTheme.palette.labelPrimary, modifier = Modifier.weight(1f))
            Text("$percent%", style = Caption, color = UaTheme.palette.labelSecondary)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallRoundIconButton(AppIcons.Minus, onDecrease, contentDescription = decreaseDescription,
                background = Color.Transparent)
            Slider(
                value = level.coerceIn(0f, 1f), onValueChange = onValueChange,
                colors = sliderColors, interactionSource = interaction,
                thumb = { SliderDefaults.Thumb(interaction, colors = sliderColors, thumbSize = DpSize(8.dp, 48.dp)) },
                modifier = Modifier.weight(1f).height(48.dp).padding(horizontal = 8.dp)
                    .testTag(sliderTag).semantics { contentDescription = label },
            )
            SmallRoundIconButton(AppIcons.Plus, onIncrease, contentDescription = increaseDescription,
                background = Color.Transparent)
        }
    }
}
