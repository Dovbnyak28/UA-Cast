package com.uacastplayer.player

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.M3uChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlayerFreshRequestTest {
    private val store = ViewModelStore()
    private val channels = listOf(M3uChannel(displayName = "A", streamUrl = "http://127.0.0.1:1/a.ts"))
    private fun player(): PlayerViewModel {
        val app: Application = ApplicationProvider.getApplicationContext()
        return ViewModelProvider.create(store, ViewModelProvider.AndroidViewModelFactory(app),
            MutableCreationExtras())[PlayerViewModel::class.java]
    }
    @After fun close() { store.clear(); PlaybackActivity.setActive(false) }

    @Test fun `fresh session does not inherit pause of closed session`() {
        val vm = player()
        vm.start(channels, 0)
        vm.togglePlayback()
        assertFalse(vm.player.playWhenReady)
        vm.releasePlayback()
        vm.start(channels, 0)
        assertTrue(vm.player.playWhenReady)
    }

    @Test fun `reattach after rotation or collapse preserves user pause`() {
        val vm = player()
        val request = PlayerRequest(channels, 0)
        vm.start(channels, 0, request = request)
        vm.togglePlayback()
        vm.start(channels, 0, request = request)
        assertFalse(vm.player.playWhenReady)
    }

    @Test fun `new channel request in background prepares only on foreground`() {
        val vm = player()
        vm.start(channels, 0)
        vm.onEnterBackground(false)
        vm.releasePlayback()
        vm.start(channels, 0)
        assertEquals(Player.STATE_IDLE, vm.player.playbackState)
        vm.onReturnToForeground()
        assertTrue(vm.player.playWhenReady)
        assertEquals(Player.STATE_BUFFERING, vm.player.playbackState)
    }

    @Test fun `fresh request does not prepare local media under remote ownership`() {
        val vm = player()
        vm.start(channels, 0)
        vm.togglePlayback()
        vm.releasePlayback()
        vm.setRemoteCastingForLifecycleTest(chromecast = true, dlna = false)
        vm.start(channels, 0)
        assertEquals(Player.STATE_IDLE, vm.player.playbackState)
        assertFalse(vm.player.playWhenReady)
    }
}
