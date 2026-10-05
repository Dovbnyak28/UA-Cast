package com.uacastplayer.player

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android controller/looper contract; no receiver, Activity, network or private playlist. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class MediaSessionRuntimeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var engine: ExoPlayer
    private lateinit var adapter: SessionPlayer
    private lateinit var session: MediaSession
    private lateinit var controller: MediaController
    private var policy = SessionPlayerControls(true, true, false, true, true)
    private var next = 0
    private var previous = 0
    private var playCommands = 0
    private var prepareCommands = 0

    @Before fun connect() {
        lateinit var pending: ListenableFuture<MediaController>
        onMain {
            val context = instrumentation.targetContext
            engine = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri("https://unused.invalid/never-prepared.mp4"))
            }
            adapter = SessionPlayer(engine, { policy }, SessionPlayerActions(
                playWhenReady = { playCommands++; engine.playWhenReady = it },
                prepare = { prepareCommands++ }, stop = { engine.stop() },
                next = { next++ }, previous = { previous++ },
            ))
            session = MediaSession.Builder(context, adapter).build()
            pending = MediaController.Builder(context, session.token).buildAsync()
        }
        controller = pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    @After fun release() = onMain {
        if (::controller.isInitialized) controller.release()
        if (::session.isInitialized) session.release()
        if (::adapter.isInitialized) adapter.release()
        else if (::engine.isInitialized) engine.release()
    }

    @Test fun singletonTimelineRoutesAllFourChannelCommandsExactlyOnce() {
        onMain {
            assertEquals(1, controller.mediaItemCount)
            assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            controller.seekToNextMediaItem()
            controller.seekToNext()
            controller.seekToPreviousMediaItem()
            controller.seekToPrevious()
        }
        awaitMain { next == 2 && previous == 2 }
        onMain { assertEquals(0, prepareCommands); assertEquals(0, playCommands) }
    }

    @Test fun remoteOwnershipRevokesLocalCommandsForExistingController() {
        onMain {
            policy = policy.copy(canPlay = false, canStop = false)
            adapter.refreshCommands()
            // Queue against the same connected controller while capability callbacks are in flight.
            controller.play()
            controller.prepare()
        }
        awaitMain { !controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        onMain {
            controller.play()
            controller.prepare()
            assertFalse(controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        }
        instrumentation.waitForIdleSync()
        onMain {
            assertEquals(0, playCommands)
            assertEquals(0, prepareCommands)
            assertFalse(engine.playWhenReady)
            assertEquals(Player.STATE_IDLE, engine.playbackState)
        }
    }

    @Test fun handbackRestoresIntentAndBoundaryChangesRevokeNavigation() {
        onMain {
            policy = policy.copy(canPlay = false, canGoNext = false, canGoPrevious = false)
            adapter.refreshCommands()
        }
        awaitMain { !controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) }
        onMain {
            controller.seekToNext()
            controller.seekToPrevious()
            policy = policy.copy(canPlay = true)
            adapter.refreshCommands()
        }
        awaitMain { controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        onMain { controller.play() }
        awaitMain { playCommands == 1 }
        onMain { controller.pause() }
        awaitMain { playCommands == 2 }
        onMain { assertEquals(0, next + previous); assertFalse(engine.playWhenReady) }
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync { action() }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        var satisfied = false
        while (!satisfied && SystemClock.uptimeMillis() < deadline) {
            onMain { satisfied = condition() }
            if (!satisfied) SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("MediaSession runtime callback did not complete", satisfied)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val POLL_MILLIS = 10L
    }
}
