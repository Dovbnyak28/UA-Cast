package com.uacastplayer.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.locks.LockSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Tests the SDK command/capability contract, replacing tests of the removed callback helper. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionPlayerControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val engine = ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri("https://unused.invalid/one.mp4"))
    }
    private var policy = SessionPlayerControls(true, true, false, true, true)
    private var next = 0
    private var previous = 0
    private var prepare = 0
    private var play = 0
    private val player = SessionPlayer(engine, { policy }, SessionPlayerActions(
        playWhenReady = { play++; engine.playWhenReady = it },
        prepare = { prepare++ }, stop = { engine.stop() },
        next = { next++ }, previous = { previous++ },
    ))
    private val session = MediaSession.Builder(context, player).build()
    private var controller: MediaController? = null

    private fun connected(): MediaController {
        val future = MediaController.Builder(context, session.token).buildAsync()
        await { future.isDone }
        return future.get().also { controller = it }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1))
        }
        assertTrue("Media3 controller callback did not complete", condition())
    }

    @After fun close() {
        controller?.release()
        session.release()
        player.release()
    }

    @Test fun `singleton timeline exposes and executes channel commands once`() {
        val remote = connected()
        assertEquals(1, remote.mediaItemCount)
        assertTrue(remote.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        remote.seekToNextMediaItem()
        await { next == 1 }
        remote.seekToPreviousMediaItem()
        await { previous == 1 }
        remote.seekToNext()
        await { next == 2 }
        remote.seekToPrevious()
        await { previous == 2 }
        assertEquals(0, prepare)
    }

    @Test fun `remote ownership removes local play prepare and unsafe playlist mutations`() {
        val remote = connected()
        policy = policy.copy(canPlay = false, canStop = false)
        player.refreshCommands()
        await { !remote.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        remote.play()
        remote.prepare()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, play)
        assertEquals(0, prepare)
        assertEquals(Player.STATE_IDLE, engine.playbackState)
        assertFalse(remote.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(remote.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))
    }

    @Test fun `local handback restores commands without replacing the session`() {
        val remote = connected()
        policy = policy.copy(canPlay = false)
        player.refreshCommands()
        await { !remote.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        policy = policy.copy(canPlay = true)
        player.refreshCommands()
        await { remote.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        remote.play()
        await { play == 1 }
        remote.pause()
        await { play == 2 }
        assertFalse(engine.playWhenReady)
    }

    @Test fun `channel boundaries revoke commands for an already connected controller`() {
        val remote = connected()
        policy = policy.copy(canGoNext = false, canGoPrevious = false)
        player.refreshCommands()
        await { !remote.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
        remote.seekToNextMediaItem()
        remote.seekToPrevious()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, next + previous)
    }
}
