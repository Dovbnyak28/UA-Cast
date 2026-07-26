package com.uacastplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.uacastplayer.R
import com.uacastplayer.player.PlayerViewModel

/** internal, not private - reused by [com.uacastplayer.ui.player.MiniPlayerBar], and called from
 * two different sites within [PlayerScreen] (inline and fullscreen) - each call site is a distinct
 * composition node, so switching between them (e.g. collapsing fullscreen into the mini-bar)
 * disposes one `PlayerView` and creates another. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun VideoSurface(viewModel: PlayerViewModel, resizeMode: Int, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            // Inflated from res/layout/player_view.xml instead of PlayerView(ctx) purely to get
            // surface_type=texture_view applied - see that file's doc for why: a SurfaceView (the
            // constructor default) under Compose overlay buttons in the same Box was swallowing
            // their taps. useController is still set here since app:use_controller in that layout
            // only seeds PlayerView's initial value, not a persistent binding.
            (android.view.LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                player = viewModel.player
                useController = false
            }
        },
        update = { view ->
            // update() can run on every recomposition of this call site even though the Player
            // instance hasn't changed - reassigning PlayerView.player unconditionally resets its
            // internal surface binding each time, which is what caused an occasional black frame
            // right after a fullscreen<->mini-bar transition (the newly composed PlayerView and the
            // about-to-be-disposed old one briefly both held the same live Player).
            if (view.player !== viewModel.player) view.player = viewModel.player
            view.resizeMode = resizeMode
        },
        // Without this, a disposed PlayerView keeps its `player` reference alive - the Player
        // itself thinks it still has a video output attached here even though this View is gone,
        // which is exactly the dangling-surface race the black-frame bug above comes from.
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
