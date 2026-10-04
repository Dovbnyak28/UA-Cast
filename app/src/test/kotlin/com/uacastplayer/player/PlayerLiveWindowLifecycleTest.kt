package com.uacastplayer.player

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.M3uChannel
import kotlin.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Delivers a live-window error at the listener boundary; no live provider or decoder is needed. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlayerLiveWindowLifecycleTest {
    private val store = ViewModelStore()

    @After fun close() {
        store.clear()
        PlaybackActivity.setActive(false)
    }

    @Test fun `foreground live-window recovery still prepares at the default position`() {
        val model = playingModel()
        model.player.seekTo(15_000L)

        deliverLiveWindowError(model)

        assertEquals(0L, model.player.currentPosition)
        assertEquals(Player.STATE_BUFFERING, model.player.playbackState)
        assertTrue(model.player.playWhenReady)
    }

    @Test fun `live-window recovery in background waits for foreground before preparing`() {
        val model = playingModel()
        model.onEnterBackground(false)

        deliverLiveWindowError(model)

        assertIdleAndPaused(model)
        model.onReturnToForeground()
        assertEquals(Player.STATE_BUFFERING, model.player.playbackState)
        assertTrue(model.player.playWhenReady)
    }

    @Test fun `live-window error after user pause does not prepare until explicit play`() {
        val model = playingModel()
        model.togglePlayback()

        deliverLiveWindowError(model)

        assertIdleAndPaused(model)
        model.onEnterBackground(false)
        model.onReturnToForeground()
        assertIdleAndPaused(model)
        model.togglePlayback()
        assertEquals(Player.STATE_BUFFERING, model.player.playbackState)
        assertTrue(model.player.playWhenReady)
    }

    @Test fun `late live-window error cannot prepare under Chromecast ownership`() {
        assertRemoteOwnership(chromecast = true)
    }

    @Test fun `late live-window error cannot prepare under DLNA ownership`() {
        assertRemoteOwnership(chromecast = false)
    }

    @Test fun `closing player clears deferred live-window recovery`() {
        val model = playingModel()
        model.onEnterBackground(false)
        deliverLiveWindowError(model)

        model.releasePlayback()
        model.onReturnToForeground()

        assertEquals(0, model.player.mediaItemCount)
        assertIdleAndPaused(model)
    }

    @Test fun `sleep expiry clears deferred live-window recovery`() {
        val model = playingModel()
        model.onEnterBackground(false)
        deliverLiveWindowError(model)

        model.sleepTimer.start(Duration.ZERO)
        model.onReturnToForeground()

        assertIdleAndPaused(model)
    }

    private fun playingModel(): PlayerViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[
            PlayerViewModel::class.java
        ].also {
            it.start(listOf(M3uChannel("Live", "http://127.0.0.1:1/live.m3u8")), 0)
        }
    }

    private fun deliverLiveWindowError(model: PlayerViewModel) {
        // Model the error's idle engine before invoking the same listener production registered.
        // Deliberately do not drain unrelated asynchronous loader callbacks from the unused URL.
        model.player.stop()
        val listener = PlayerViewModel::class.java.getDeclaredField("listener").let {
            it.isAccessible = true
            it.get(model) as Player.Listener
        }
        listener.onPlayerError(PlaybackException(
            "Controlled live-window failure", null, PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        ))
    }

    private fun assertRemoteOwnership(chromecast: Boolean) {
        val model = playingModel()
        model.setRemoteCastingForLifecycleTest(chromecast, dlna = !chromecast)

        deliverLiveWindowError(model)

        assertEquals("Remote receiver must remain the only loader", Player.STATE_IDLE, model.player.playbackState)
    }

    private fun assertIdleAndPaused(model: PlayerViewModel) {
        assertEquals(Player.STATE_IDLE, model.player.playbackState)
        assertFalse(model.player.playWhenReady)
    }
}
