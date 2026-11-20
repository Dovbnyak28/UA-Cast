package com.uacastplayer.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.uacastplayer.core.ui.findActivity

/** One visible screen owns icon contrast; dark player chrome overrides light phone navigation. */
@Composable
internal fun SystemBarAppearance(dark: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        // API 24/25 cannot draw dark navigation buttons. Keep their background black in light mode.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) setLegacyNavigationBackground(activity.window)
    }
}

@Suppress("DEPRECATION") // Only called on API 24/25, before dark navigation icons existed.
private fun setLegacyNavigationBackground(window: android.view.Window) {
    window.navigationBarColor = android.graphics.Color.BLACK
}
