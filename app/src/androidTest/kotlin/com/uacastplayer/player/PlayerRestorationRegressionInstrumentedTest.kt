package com.uacastplayer.player

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.lockChannel
import com.uacastplayer.resetParentalControl
import com.uacastplayer.setParentalControlPin
import com.uacastplayer.testsupport.FakeOriginServer
import com.uacastplayer.testsupport.appViewModelOf
import com.uacastplayer.testsupport.loadTestPlaylist
import com.uacastplayer.testsupport.openChannelViaSearch
import com.uacastplayer.testsupport.setAutoSkipDeadChannels
import com.uacastplayer.testsupport.skipOnboarding
import com.uacastplayer.testsupport.waitForChannelsLoaded
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerRestorationRegressionInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var origin: FakeOriginServer
    private var oldAutoSkip = true

    @Before fun setup() {
        oldAutoSkip = setAutoSkipDeadChannels(InstrumentationRegistry.getInstrumentation().targetContext, false)
        origin = FakeOriginServer.startWithChannels(channelCount = 3)
        rule.activityRule.scenario.onActivity {
            skipOnboarding(it)
            appViewModelOf(it).resetParentalControl()
            loadTestPlaylist(it, origin)
        }
        rule.waitForChannelsLoaded(origin)
    }

    @After fun cleanup() {
        rule.activityRule.scenario.onActivity { appViewModelOf(it).resetParentalControl() }
        origin.shutdown()
        setAutoSkipDeadChannels(InstrumentationRegistry.getInstrumentation().targetContext, oldAutoSkip)
    }

    @Test fun recreatedPlayerMustKeepLockedChannelsOutOfNavigation() {
        rule.activityRule.scenario.onActivity {
            val app = appViewModelOf(it)
            app.viewModelScope.launch {
                assertTrue(app.setParentalControlPin("1234"))
                app.lockChannel(app.playlistState.value.channels.first { channel -> channel.displayName == "Channel 2" })
            }
            assertFalse(app.parentalControlUnlocked.value)
        }
        rule.waitUntil(10_000) {
            val app = appViewModelOf(rule.activity)
            app.parentalControlPinSet.value && app.lockedChannelKeys.value.isNotEmpty()
        }
        rule.openChannelViaSearch("Channel 1")
        rule.activityRule.scenario.onActivity {
            val player = ViewModelProvider(it)[PlayerViewModel::class.java]
            assertFalse("The ordinary tap path is filtered", player.uiState.value.nextChannelsPreview.any {
                channel -> channel.channel.displayName == "Channel 2"
            })
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.waitUntil(10_000) {
            var hasPreview = false
            rule.activityRule.scenario.onActivity {
                hasPreview = ViewModelProvider(it)[PlayerViewModel::class.java].uiState.value.nextChannelsPreview.isNotEmpty()
            }
            hasPreview
        }
        rule.activityRule.scenario.onActivity {
            val app = appViewModelOf(it)
            val player = ViewModelProvider(it)[PlayerViewModel::class.java]
            assertFalse(app.parentalControlUnlocked.value)
            assertTrue(app.lockedChannelKeys.value.isNotEmpty())
            val preview = player.uiState.value.nextChannelsPreview.map { channel -> channel.channel.displayName }
            android.util.Log.i("AuditRestoration", "after recreate navigation=$preview")
            assertFalse("Restoration must apply the same parental filter as a tap; actual=$preview", "Channel 2" in preview)
        }
    }

    @Test fun retainedPlayerMustNotReplaceItsMediaItemOnActivityRecreation() {
        rule.openChannelViaSearch("Channel 1")
        val replacements = AtomicInteger()
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) replacements.incrementAndGet()
            }
        }
        lateinit var before: PlayerViewModel
        rule.activityRule.scenario.onActivity {
            before = ViewModelProvider(it)[PlayerViewModel::class.java]
            before.player.addListener(listener)
        }
        try {
            rule.activityRule.scenario.recreate()
            rule.waitForIdle()
            rule.activityRule.scenario.onActivity {
                assertTrue(before === ViewModelProvider(it)[PlayerViewModel::class.java])
            }
            android.util.Log.i("AuditRestoration", "media replacements during recreation=${replacements.get()}")
            assertEquals("Reattaching UI to a retained engine is not a new playback request", 0, replacements.get())
        } finally {
            rule.activityRule.scenario.onActivity { before.player.removeListener(listener) }
        }
    }
}
