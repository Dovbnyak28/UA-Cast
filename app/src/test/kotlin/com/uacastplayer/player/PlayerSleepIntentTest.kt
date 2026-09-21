package com.uacastplayer.player

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.M3uChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class SleepTestApplication : Application(), PlayerCastPortOwner {
    val port = SleepTestCastPort()
    override val playerCastPort: PlayerCastPort get() = port
}

class SleepTestCastPort : PlayerCastPort {
    override val state = MutableStateFlow(PlayerCastState())
    override val sideEffects = MutableSharedFlow<PlayerCastSideEffect>(extraBufferCapacity = 8)
    var stops = 0
    override fun setActiveChannel(channel: PlayerCastChannel) = Unit
    override fun stopPlayback() {
        stops++
        state.value = PlayerCastState()
        sideEffects.tryEmit(PlayerCastSideEffect.ResumeLocalPlayer)
    }
}

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = SleepTestApplication::class)
class PlayerSleepIntentTest {
    private val application = ApplicationProvider.getApplicationContext<SleepTestApplication>()
    private val store = ViewModelStore()
    private fun player(): PlayerViewModel = ViewModelProvider(
        store, ViewModelProvider.AndroidViewModelFactory(application),
    )[PlayerViewModel::class.java].also {
        it.start(listOf(M3uChannel("One", "http://127.0.0.1:9/a"), M3uChannel("Two", "http://127.0.0.1:9/b")), 0)
    }
    private fun settle() = shadowOf(Looper.getMainLooper()).idle()
    private fun expire(player: PlayerViewModel) {
        player.sleepTimer.start(Duration.ZERO)
        settle()
    }

    @After fun close() {
        store.clear()
        PlaybackActivity.setActive(false)
    }

    @Test fun `expiry while backgrounded cancels the policy resume`() {
        val player = player()
        player.onEnterBackground(false)
        expire(player)
        player.onReturnToForeground()
        assertFalse(player.player.playWhenReady)
        assertEquals(Player.STATE_IDLE, player.player.playbackState)
        assertEquals(null, player.sleepTimer.remainingMillis.value)
    }

    @Test fun `expiry stops remote without a synchronous or late local handback`() {
        val player = player()
        application.port.state.value = PlayerCastState(isConnected = true)
        settle()
        expire(player)
        application.port.sideEffects.tryEmit(PlayerCastSideEffect.ApplyPendingChannelSwitch(1))
        application.port.sideEffects.tryEmit(PlayerCastSideEffect.ResumeLocalPlayer)
        settle()
        assertEquals(1, application.port.stops)
        assertFalse(player.player.playWhenReady)
        assertFalse(player.uiState.value.isCasting)
        assertEquals("One", player.uiState.value.currentChannel?.displayName)
    }

    @Test fun `explicit play after expiry can start again`() {
        val player = player()
        expire(player)
        player.togglePlayback()
        assertTrue(player.player.playWhenReady)
    }

    @Test fun `system play after sleep expiry goes through explicit playback intent`() {
        val player = player()
        expire(player)
        requireNotNull(player.mediaSession).player.play()
        assertTrue(player.player.playWhenReady)
        player.onEnterBackground(false)
        player.onReturnToForeground()
        // A raw engine play would leave sleepTimerExpired true and suppress this resume.
        assertTrue(player.player.playWhenReady)
    }

    @Test fun `system play and prepare cannot restart local engine during either remote route`() {
        val player = player()
        val system = requireNotNull(player.mediaSession).player
        for (chromecast in listOf(true, false)) {
            player.player.pause()
            player.player.stop()
            player.setRemoteCastingForLifecycleTest(chromecast, dlna = !chromecast)
            assertFalse(system.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            system.play()
            system.prepare()
            assertFalse(player.player.playWhenReady)
            assertEquals(Player.STATE_IDLE, player.player.playbackState)
        }
    }

    @Test fun `system controls cannot resurrect a backgrounded or closed player`() {
        val player = player()
        val system = requireNotNull(player.mediaSession).player
        player.onEnterBackground(false)
        assertFalse(system.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        system.play()
        assertFalse(player.player.playWhenReady)
        player.onReturnToForeground()
        player.releasePlayback()
        assertFalse(system.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        system.play()
        system.prepare()
        assertEquals(0, player.player.mediaItemCount)
        assertEquals(Player.STATE_IDLE, player.player.playbackState)
    }

    @Test fun `deferred cast handback cannot survive later timer expiry`() {
        val player = player()
        player.player.pause()
        player.onEnterBackground(false)
        player.resumeLocalPlayback()
        expire(player)
        player.onReturnToForeground()
        assertFalse(player.player.playWhenReady)
    }
}
