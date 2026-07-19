package com.uacastplayer.ui.cast

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.uacastplayer.R

/**
 * Thin Compose wrapper around the platform Cast route-picker button; requires a FragmentActivity host.
 *
 * Wrapped in [Theme.UaCastPlayer.CastButton][R.style.Theme_UaCastPlayer_CastButton] so the icon
 * tints light-on-dark like the rest of the UI - see that style for why this is needed.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_UaCastPlayer_CastButton)
            MediaRouteButton(themedContext).also { button ->
                CastButtonFactory.setUpMediaRouteButton(themedContext, button)
            }
        },
        modifier = modifier,
    )
}
