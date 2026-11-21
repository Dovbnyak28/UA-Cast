package com.uacastplayer.data.prefs

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlaybackPreferenceObserverTest {
    private val preferences = AppPreferences(ApplicationProvider.getApplicationContext<Context>())

    @Test fun `only playback settings notify their owner`() {
        var notifications = 0
        val subscription = preferences.observePlaybackChanges { notifications++ }
        preferences.wrapAroundEnabled = !preferences.wrapAroundEnabled
        preferences.autoSkipDeadEnabled = !preferences.autoSkipDeadEnabled
        preferences.iconWifiOnly = !preferences.iconWifiOnly
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, notifications)
        subscription.close()
        preferences.wrapAroundEnabled = !preferences.wrapAroundEnabled
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, notifications)
    }

    @Test fun `close rejects a queued callback as well as future changes`() {
        var notifications = 0
        val subscription = preferences.observePlaybackChanges { notifications++ }
        // Write off Main so Android has to post its notification before the owner closes.
        Thread { preferences.wrapAroundEnabled = !preferences.wrapAroundEnabled }.apply { start(); join() }
        subscription.close()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, notifications)
    }
}
