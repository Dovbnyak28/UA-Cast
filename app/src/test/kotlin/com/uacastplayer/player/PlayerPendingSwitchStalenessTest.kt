package com.uacastplayer.player

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.playlist.M3uChannel
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A debounced [PlayerViewModel.requestSwitch] outliving the channel list it was requested against.
 *
 * `requestSwitch` schedules a call to the private `switchToIndexImmediate(index)` after
 * `CHANNEL_SWITCH_DEBOUNCE_MILLIS`, cancelling only a *previous* debounce - not a switch that lands
 * through some other path in between. `start()` is one such path: it calls
 * `switchToIndexImmediate` directly (to open the first channel of whatever list it was just given)
 * without cancelling a debounce left over from the list it is replacing. So a tap registered right
 * before a fresh playlist loads (a source switch, a refresh that changes indices, reopening the
 * player on a different group) can fire *after* `start()` has already moved on, applying a now-stale
 * index to the new channel list instead of the one the tap was actually made against.
 *
 * `switchToIndexImmediate` already resets `retryJob`/`stallRecoveryJob` for exactly this class of
 * problem (see its own comment on `retryJob`) - `pendingSwitchJob` was the one left out.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlayerPendingSwitchStalenessTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        // Runs onCleared, releasing the ExoPlayer and the process-wide live-instance count that
        // PlayerViewModel guards itself with.
        viewModelStore.clear()
        PlaybackActivity.setActive(false)
    }

    private fun player(): PlayerViewModel {
        val provider = ViewModelProvider.create(
            store = viewModelStore,
            factory = ViewModelProvider.AndroidViewModelFactory(application),
            extras = MutableCreationExtras(),
        )
        return provider[PlayerViewModel::class.java]
    }

    private fun channel(name: String) =
        M3uChannel(displayName = name, streamUrl = "http://127.0.0.1:1/$name.ts")

    /** Lets the main looper - which Robolectric holds paused - run whatever the player posted to it. */
    private fun settle(millis: Long = 500L) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    @Test
    fun `a debounced switch does not apply to a channel list loaded after it was requested`() {
        val player = player()
        player.start(listOf(channel("A"), channel("B")), startIndex = 0)

        // Requested against the two-channel list above, index 1 ("B") - still inside its debounce,
        // not applied yet.
        player.requestSwitch(1)

        // A fresh playlist replaces the channel list before that debounce has fired.
        player.start(listOf(channel("X"), channel("Y"), channel("Z")), startIndex = 0)
        assertEquals("X", player.uiState.value.currentChannel?.displayName)

        // Let the stale debounce run its course.
        settle()

        assertEquals(
            "a switch requested against the old playlist must not apply to the new one",
            "X",
            player.uiState.value.currentChannel?.displayName,
        )
    }
}
