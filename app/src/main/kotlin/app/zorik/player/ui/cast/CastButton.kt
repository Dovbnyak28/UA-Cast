package app.zorik.player.ui.cast

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import app.zorik.player.R
import app.zorik.player.core.concurrent.AppDispatchers
import app.zorik.player.log.AppLog
import kotlinx.coroutines.asExecutor

private val castModuleExecutor = AppDispatchers.io.asExecutor()

/**
 * Thin Compose wrapper around the platform Cast route-picker button; requires a FragmentActivity host.
 *
 * Wrapped in [Theme.ZorikPlayer.CastButton][R.style.Theme_ZorikPlayer_CastButton] so the icon
 * tints light-on-dark like the rest of the UI - see that style for why this is needed.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_ZorikPlayer_CastButton)
            MediaRouteButton(themedContext).also { button ->
                CastButtonFactory.setUpMediaRouteButton(themedContext.applicationContext, castModuleExecutor, button)
                    .addOnFailureListener { error ->
                        AppLog.w("CastButton") { "Cast button unavailable: ${error.javaClass.simpleName}" }
                    }
            }
        },
        modifier = modifier,
    )
}
