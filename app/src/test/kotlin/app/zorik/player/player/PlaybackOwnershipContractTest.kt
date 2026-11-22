package app.zorik.player.player

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.playlist.M3uChannel
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = SleepTestApplication::class)
class PlaybackOwnershipContractTest {
    private val app = ApplicationProvider.getApplicationContext<SleepTestApplication>()
    private val store = ViewModelStore()

    @After fun close() { store.clear(); PlaybackActivity.setActive(false) }

    private fun player(): PlayerViewModel = ViewModelProvider(
        store, ViewModelProvider.AndroidViewModelFactory(app),
    )[PlayerViewModel::class.java].also {
        // Keep the fixture offline: remote ownership sets a media item without preparing it.
        it.player.pause()
        it.setRemoteCastingForLifecycleTest(chromecast = true, dlna = false)
        it.start(List(3) { index -> M3uChannel("Channel $index", "http://127.0.0.1:9/$index") }, 1)
    }

    @Test fun pausedBackgroundPlayerRevokesBothUiAndSystemCommandsImmediately() {
        val vm = player()
        vm.setRemoteCastingForLifecycleTest(false, false)
        val system = requireNotNull(vm.mediaSession).player
        vm.player.pause()
        assertTrue(vm.uiState.value.canControlPlayback)
        vm.onEnterBackground(false)
        assertFalse("UI still offers a command that its handler rejects", vm.uiState.value.canControlPlayback)
        assertFalse(vm.uiState.value.canGoNext)
        assertFalse(vm.uiState.value.canGoPrevious)
        assertFalse(system.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        vm.togglePlayback()
        assertFalse(vm.player.playWhenReady)
        vm.onReturnToForeground()
        assertTrue(vm.uiState.value.canControlPlayback)
        assertTrue(system.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertFalse("A user pause must survive a background round trip", vm.player.playWhenReady)
    }

    @Test fun overlappingCastRoutesKeepLocalPlaybackBlockedUntilBothReleaseIt() {
        val vm = player()
        val engine = vm.player
        val system = requireNotNull(vm.mediaSession).player
        for ((cast, dlna) in listOf(true to false, false to true, true to true, false to true)) {
            vm.setRemoteCastingForLifecycleTest(cast, dlna)
            assertFalse(vm.uiState.value.canControlPlayback)
            assertFalse(vm.uiState.value.canSeek)
            assertTrue(vm.uiState.value.canGoNext)
            assertTrue(system.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            system.play()
            vm.togglePlayback()
            assertFalse(engine.playWhenReady)
            assertEquals(Player.STATE_IDLE, engine.playbackState)
        }
        vm.setRemoteCastingForLifecycleTest(false, false)
        assertTrue(vm.uiState.value.canControlPlayback)
        assertSame(engine, vm.player)
        assertSame(system, requireNotNull(vm.mediaSession).player)
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
    }

    @Test fun thirtyRapidSelectionsApplyOnlyTheLastRequestWithoutReplacingThePlayer() {
        val vm = player()
        val engine = vm.player
        val system = requireNotNull(vm.mediaSession).player
        repeat(30) { vm.navigation.requestSwitch(it % 3) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        assertEquals("Channel 2", vm.uiState.value.currentChannel?.displayName)
        vm.wrapAroundEnabled = false
        assertFalse(vm.uiState.value.canGoNext)
        assertFalse(system.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(vm.uiState.value.canGoPrevious)
        assertSame(engine, vm.player)
        assertFalse(engine.playWhenReady)
        assertEquals(1, engine.mediaItemCount)
        vm.releasePlayback()
        assertFalse(vm.uiState.value.canControlPlayback)
        assertFalse(system.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertEquals(0, engine.mediaItemCount)
    }

    @Test fun rapidPlayPauseUsesCurrentEngineIntentAndImmediatelyUpdatesUi() {
        val vm = player()
        vm.setRemoteCastingForLifecycleTest(false, false)
        val engine = vm.player
        repeat(30) { index ->
            vm.togglePlayback()
            assertEquals(index % 2 == 0, engine.playWhenReady)
            assertEquals(engine.playWhenReady, vm.uiState.value.wantsToPlay)
        }
        assertSame(engine, vm.player)
        assertFalse(engine.playWhenReady)
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
    }
}
