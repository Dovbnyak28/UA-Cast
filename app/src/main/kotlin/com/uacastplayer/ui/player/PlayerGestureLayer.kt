package com.uacastplayer.ui.player
import com.uacastplayer.ui.theme.UaTheme

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.media3.common.VideoSize
import kotlin.math.roundToInt
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

    /** Denominator for a ratio that cannot be expressed exactly as the frame's own dimensions. */
    private const val RATIO_PRECISION = 1000

    /**
     * Android rejects a PiP aspect ratio outside roughly 1:2.39 .. 2.39:1, throwing
     * IllegalArgumentException out of `enterPictureInPictureMode` - so whatever the stream reports
     * has to be clamped rather than passed through, or a corrupt reported size crashes the app on
     * the PiP button.
     *
     * The bounds are integers pre-scaled by [RATIO_PRECISION] rather than a float `coerceIn`,
     * because the rounding has to land *inside* the range: clamping to 1/2.39 and then truncating
     * 418.4 gives 0.418, which is below the limit and throws at exactly the point this is meant to
     * protect. 2.39:1 becomes 2390/1000; 1:2.39 rounds up to 419/1000, just inside.
     */
    private const val MIN_SCALED_ASPECT = 419
    private const val MAX_SCALED_ASPECT = 2390

    /** Used when the stream has not reported a size yet (nothing decoded, or an audio-only
     * channel). 16:9 is the right guess for "unknown TV channel"; it is wrong as a *constant*,
     * which is what it used to be. */
    private val FALLBACK_ASPECT = Rational(16, 9)

    /**
     * Display aspect ratio for [width] x [height] sample dimensions with pixel aspect
     * [pixelWidthHeightRatio].
     *
     * The pixel ratio is not a detail to skip on an IPTV app: broadcast SD is routinely anamorphic
     * (704x576 or 720x576 samples carrying a 4:3 or 16:9 *display* picture), so sample dimensions
     * alone would describe a shape the viewer never sees. media3 reports 1.0 for square pixels,
     * which is the common HD case and leaves the arithmetic untouched.
     */
    fun aspectRatioFor(width: Int, height: Int, pixelWidthHeightRatio: Float = 1f): Rational {
        val par = squarePixelsUnless(pixelWidthHeightRatio)
        val ratio = if (width > 0 && height > 0) width.toFloat() * par / height.toFloat() else Float.NaN
        if (!ratio.isFinite() || ratio <= 0f) return FALLBACK_ASPECT
        val scaled = (ratio * RATIO_PRECISION).roundToInt()
        // Kept exact for the overwhelmingly common square-pixel, in-range case (1920x1080 stays
        // 1920:1080) so the window matches the decoded frame to the pixel.
        return if (par == 1f && scaled in MIN_SCALED_ASPECT..MAX_SCALED_ASPECT) {
            Rational(width, height)
        } else {
            Rational(scaled.coerceIn(MIN_SCALED_ASPECT, MAX_SCALED_ASPECT), RATIO_PRECISION)
        }
    }

    /** media3 reports 0, NaN or infinity for pixel aspect on some streams; all of those mean "no
     * information", which is square pixels, not a ratio to propagate into the arithmetic. */
    private fun squarePixelsUnless(pixelWidthHeightRatio: Float): Float =
        if (pixelWidthHeightRatio.isFinite() && pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f

    /**
     * [sourceRectHint] is the on-screen rectangle the video currently occupies, in window
     * coordinates. Without it the system cross-fades into the PiP window; with it, it scales the
     * actual video out of where it already was.
     */
    fun enter(activity: Activity, videoSize: VideoSize, sourceRectHint: Rect?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        activity.enterPictureInPictureMode(buildParams(videoSize, sourceRectHint))
    }

    /**
     * Keeps the activity's PiP params current *before* anyone asks to enter, which is what
     * `setAutoEnterEnabled` needs: from API 31 the system enters PiP by itself when the user
     * gestures home, using whatever params were last set. Set only while video is actually playing
     * - auto-entering PiP for a paused or errored channel would put an empty window on screen.
     */
    fun syncParams(activity: Activity, videoSize: VideoSize, sourceRectHint: Rect?, autoEnter: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            activity.setPictureInPictureParams(buildParams(videoSize, sourceRectHint, autoEnter))
        }
    }

    /** Both callers gate on the SDK level themselves, but lint cannot see through the call - and a
     * silent `@Suppress` here would also hide a genuinely unguarded future caller. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildParams(
        videoSize: VideoSize,
        sourceRectHint: Rect?,
        autoEnter: Boolean = false,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatioFor(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio))
        sourceRectHint?.let(builder::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
        return builder.build()
    }
}
