package app.zorik.player.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** One observer at the app theme root, inherited by nested player themes and all controls. */
internal val LocalSystemMotionEnabled = staticCompositionLocalOf<Boolean?> { null }

internal fun systemMotionEnabled(scale: Float): Boolean = scale > 0f

@Composable
internal fun rememberSystemMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read(): Boolean = systemMotionEnabled(runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f))
    var enabled by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
internal fun motionAllowed(): Boolean {
    if (LocalReducedEffects.current) return false
    return LocalSystemMotionEnabled.current ?: rememberSystemMotionEnabled()
}

@Composable
internal fun motionDuration(durationMillis: Int): Int = if (motionAllowed()) durationMillis else 0
