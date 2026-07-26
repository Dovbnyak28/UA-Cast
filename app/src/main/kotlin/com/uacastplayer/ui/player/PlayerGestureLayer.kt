package com.uacastplayer.ui.player
import com.uacastplayer.ui.theme.UaTheme

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.uacastplayer.player.BrightnessGestureStart
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.RadiusField

internal const val DEFAULT_BRIGHTNESS_LEVEL = 0.5f

internal enum class GestureIndicatorKind { BRIGHTNESS, VOLUME }

internal fun AudioManager?.currentVolumeFraction(): Float {
    if (this == null) return DEFAULT_BRIGHTNESS_LEVEL
    val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return DEFAULT_BRIGHTNESS_LEVEL
    return getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
}

internal fun applyWindowBrightness(activity: Activity, level: Float) {
    val window = activity.window
    val params = window.attributes
    params.screenBrightness = level.coerceIn(0.01f, 1f)
    window.attributes = params
}

/** Restores the window to following the system/auto brightness - undoes [applyWindowBrightness]. */
internal fun restoreWindowBrightness(activity: Activity) {
    val window = activity.window
    val params = window.attributes
    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = params
}

private const val MAX_SYSTEM_BRIGHTNESS = 255f

internal fun initialBrightnessLevel(activity: Activity): Float {
    val systemBrightness = try {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / MAX_SYSTEM_BRIGHTNESS
    } catch (_: Settings.SettingNotFoundException) {
        null
    }
    return BrightnessGestureStart.level(activity.window.attributes.screenBrightness, systemBrightness)
}

internal fun applyStreamVolume(audioManager: AudioManager, level: Float) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val target = (level * max).toInt().coerceIn(0, max)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}

/** Thin vertical fill bar shown while dragging in the brightness/volume zones of the fullscreen
 * player - mirrors the system overlay's shape but stays inside the app's own design system. */
@Composable
internal fun GestureLevelIndicator(kind: GestureIndicatorKind, level: Float, modifier: Modifier = Modifier) {
    Column(
        // flat by design: deliberately mirrors the system volume/brightness overlay's minimal
        // look (see the KDoc above), not the app's own raised chrome.
        modifier = modifier
            .width(44.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(RadiusField))
            .background(UaTheme.palette.surface2)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (kind == GestureIndicatorKind.BRIGHTNESS) AppIcons.Brightness else AppIcons.Volume,
            contentDescription = null,
            tint = UaTheme.palette.azure,
            modifier = Modifier.size(18.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusField / 2))
                .background(UaTheme.palette.overlayHighlight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(RadiusField / 2))
                    .background(UaTheme.palette.azure),
            )
        }
    }
}

internal object FullscreenController {
    fun apply(activity: Activity, enabled: Boolean) {
        activity.requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView,
        )
        if (enabled) {
            windowInsetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

internal object PipController {
    fun enter(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }
}
