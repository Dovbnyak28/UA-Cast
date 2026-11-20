package com.uacastplayer.player

import android.app.Application
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses a real single-item engine; only its advertised command boundary is restricted. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionPlayerAvailableCommandsRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val engine = ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri("https://unused.invalid/one.mp4"))
    }
    private val limitedEngine = CommandLimitedPlayer(engine)
    private val playIntents = mutableListOf<Boolean>()
    private var prepares = 0
    private var nextChannels = 0
    private var previousChannels = 0
    private val player = SessionPlayer(
        limitedEngine,
        controls = { SessionPlayerControls(true, true, false, true, true) },
        actions = SessionPlayerActions(
            playWhenReady = { playIntents.add(it) },
            prepare = { prepares++ },
            stop = engine::stop,
            next = { nextChannels++ },
            previous = { previousChannels++ },
        ),
    )

    @After fun releasePlayer() {
        player.release()
    }

    @Test fun `session does not advertise engine unsupported play pause`() {
        limitedEngine.denied = setOf(Player.COMMAND_PLAY_PAUSE)
        player.refreshCommands()

        assertFalse(limitedEngine.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertFalse(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
    }

    @Test fun `session does not advertise engine unsupported prepare`() {
        limitedEngine.denied = setOf(Player.COMMAND_PREPARE)
        player.refreshCommands()

        assertFalse(limitedEngine.isCommandAvailable(Player.COMMAND_PREPARE))
        assertFalse(player.isCommandAvailable(Player.COMMAND_PREPARE))
    }

    @Test fun `unsupported play pause and prepare never reach application actions`() {
        limitedEngine.denied = setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE)
        player.refreshCommands()

        player.play()
        player.pause()
        player.prepare()

        assertTrue("Unsupported playback intent reached the application", playIntents.isEmpty())
        assertEquals(0, prepares)
    }

    @Test fun `engine command removal and restoration update the same session player`() {
        assertTrue(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(player.isCommandAvailable(Player.COMMAND_PREPARE))

        limitedEngine.denied = setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE)
        player.refreshCommands()
        assertFalse(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertFalse(player.isCommandAvailable(Player.COMMAND_PREPARE))

        limitedEngine.denied = emptySet()
        player.refreshCommands()
        assertTrue(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(player.isCommandAvailable(Player.COMMAND_PREPARE))
        player.play()
        player.prepare()
        assertEquals(listOf(true), playIntents)
        assertEquals(1, prepares)
    }

    @Test fun `virtual channel commands remain available without engine playlist navigation`() {
        limitedEngine.denied = setOf(
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
        )
        player.refreshCommands()

        assertEquals(1, engine.mediaItemCount)
        assertFalse(limitedEngine.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        player.seekToNextMediaItem()
        player.seekToPreviousMediaItem()

        assertEquals(1, nextChannels)
        assertEquals(1, previousChannels)
        assertTrue(playIntents.isEmpty())
        assertEquals(0, prepares)
    }

    private class CommandLimitedPlayer(player: Player) : ForwardingPlayer(player) {
        var denied: Set<Int> = emptySet()

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().removeAll(*denied.toIntArray()).build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
    }
}
