package com.uacastplayer.ui.player

import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native resource/surface/ownership regression. Does not prepare a network stream or modify app data. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class PlayerSurfaceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun inlineTextureKeepsVisualBindingsWithoutDuplicateControls() = onMain {
        val view = inflate(R.layout.player_view)
        assertTrue(view.videoSurfaceView is TextureView)
        assertVisualBindings(view)
    }

    @Test fun pipRetainsItsSurfaceViewAndSubtitlesWithoutDuplicateControls() = onMain {
        val view = inflate(R.layout.pip_player_view)
        assertTrue(view.videoSurfaceView is SurfaceView)
        assertVisualBindings(view)
    }

    @Test fun thirtyTargetHandoffsDoNotReleaseTheOwnedPlayer() = onMain {
        val player = ExoPlayer.Builder(context).build()
        var current = inflate(R.layout.player_view)
        try {
            current.player = player
            repeat(HANDOFFS) { index ->
                val next = inflate(if (index % 2 == 0) R.layout.pip_player_view else R.layout.player_view)
                // Same attach-new/detach-old order used by Media3's documented switchTargetView.
                next.player = player
                current.player = null
                assertNull(current.player)
                assertSame(player, next.player)
                player.playWhenReady = index % 2 == 0
                assertEquals(index % 2 == 0, player.playWhenReady)
                current = next
            }
        } finally {
            current.player = null
            player.release()
        }
    }

    @Test fun surfaceHierarchyIsMateriallySmallerThanTheDefaultMedia3Layout() = onMain {
        val original = PlayerView(context).apply { useController = false }
        val surface = inflate(R.layout.player_view)
        val before = countViews(original)
        val after = countViews(surface)
        assertTrue("Removing the duplicate panel should remove real Views, not only hide them", before - after >= MIN_REMOVED_VIEWS)
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("stream", "\nMedia3 hierarchy: default=$before, video-only=$after\n")
        })
    }

    private fun assertVisualBindings(view: PlayerView) {
        assertFalse(view.useController)
        assertNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_controller))
        assertNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_buffering))
        assertNotNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_shutter))
        assertNotNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_artwork))
        assertNotNull(view.findViewById<View>(androidx.media3.ui.R.id.exo_image))
        assertNotNull(view.findViewById<SubtitleView>(androidx.media3.ui.R.id.exo_subtitles))
        assertNotNull(view.overlayFrameLayout)
        assertNotNull(view.adViewGroup)
        assertTrue(countViews(view) <= MAX_SURFACE_VIEWS)
    }

    private fun inflate(layout: Int) = LayoutInflater.from(context).inflate(layout, null) as PlayerView
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun countViews(view: View): Int = 1 + if (view is ViewGroup) {
        (0 until view.childCount).sumOf { countViews(view.getChildAt(it)) }
    } else 0

    private companion object {
        const val HANDOFFS = 30
        const val MIN_REMOVED_VIEWS = 20
        const val MAX_SURFACE_VIEWS = 12
    }
}
