package app.zorik.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.zorik.player.R
import app.zorik.player.player.PlayerViewModel
import app.zorik.player.ui.components.coverWhileOpening

/** Native video binding shared by the phone and TV layouts. The phone screen hoists movable
 * content so inline/fullscreen reuse this view; actual removal still detaches its player.
 *
 * The video is a SurfaceView (res/layout/player_view.xml): SurfaceFlinger composites its frames
 * directly, so a playing channel does not redraw the app window at the video frame rate the way a
 * TextureView did. The price is that the surface cannot follow a graphics layer above it, which is
 * why it is covered while the player's opening transform runs. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun VideoSurface(viewModel: PlayerViewModel, resizeMode: Int, modifier: Modifier = Modifier) {
    val frameState = remember(viewModel) {
        viewModel.uiState.map { it.hasRenderedVideoFrame }.distinctUntilChanged()
    }
    val initialFrame = remember(viewModel) { viewModel.uiState.value.hasRenderedVideoFrame }
    val rendered by frameState.collectAsStateWithLifecycle(initialFrame)
    val description = stringResource(if (rendered) R.string.player_frame_displayed else R.string.player_video_surface)
    AndroidView(
        factory = { ctx ->
            // XML sets the video-only content layout (no duplicate native controls) and the
            // surface type - see that file's doc. useController is still set here since
            // app:use_controller in that layout only seeds PlayerView's initial value, not a
            // persistent binding.
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
            // AndroidView accessibility is exported by the native view, not only Compose semantics.
            view.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
            view.contentDescription = description
        },
        // Without this, a disposed PlayerView keeps its `player` reference alive - the Player
        // itself thinks it still has a video output attached here even though this View is gone,
        // which is exactly the dangling-surface race the black-frame bug above comes from.
        onRelease = { it.player = null },
        modifier = modifier.coverWhileOpening(),
    )
}
