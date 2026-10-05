package com.uacastplayer.ui.player

import android.content.Context
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pins Media3's optional-child contract without constructing a decoder or changing player ownership. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(RobolectricTestRunner::class)
class PlayerSurfaceLayoutTest {
    private fun inflate(): PlayerView = LayoutInflater.from(
        ApplicationProvider.getApplicationContext<Context>(),
    ).inflate(R.layout.player_view, null) as PlayerView

    @Test fun composeControlsDoNotInflateASecondNativeController() {
        val view = inflate()
        assertFalse(view.useController)
        assertNull("Disabled controls must not still allocate their complete hierarchy", view.findViewById<View>(
            androidx.media3.ui.R.id.exo_controller,
        ))
        assertNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_controller_placeholder))
        assertNull("Compose owns the buffering indicator",
            view.findViewById<View>(androidx.media3.ui.R.id.exo_buffering))
        assertTrue("The video-only hierarchy must stay bounded", countViews(view) <= MAX_SURFACE_VIEWS)
    }

    @Test fun videoSubtitlesArtworkAndOverlayContractsRemainAvailable() {
        val view = inflate()
        assertTrue(view.videoSurfaceView is TextureView)
        assertNotNull(view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame))
        assertNotNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_shutter))
        assertNotNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_artwork))
        assertNotNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_image))
        assertNotNull(view.findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles))
        assertNotNull(view.overlayFrameLayout)
        assertNotNull(view.adViewGroup)
    }

    private fun countViews(view: View): Int = 1 + if (view is ViewGroup) {
        (0 until view.childCount).sumOf { countViews(view.getChildAt(it)) }
    } else 0

    private companion object {
        const val MAX_SURFACE_VIEWS = 12
    }
}
