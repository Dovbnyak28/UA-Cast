package com.uacastplayer.ui

import android.graphics.Bitmap
import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.testsupport.FakeOriginServer
import com.uacastplayer.testsupport.loadTestPlaylist
import com.uacastplayer.testsupport.openChannelViaSearch
import com.uacastplayer.testsupport.setAutoSkipDeadChannels
import com.uacastplayer.testsupport.skipOnboarding
import com.uacastplayer.testsupport.waitForChannelsLoaded
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fixture-only visual evidence. Run on personal hardware exclusively via run-preserved-device-tests.ps1. */
@RunWith(AndroidJUnit4::class)
class UiPolishInstrumentedTest {
    @Test fun nextChannelNamesKeepTheirDistinguishingSuffixVisible() {
        for (name in listOf("Channel 2", "Channel 3")) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(name, useUnmergedTree = true).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$name is clipped at current system font size", layout.hasVisualOverflow)
            assertEquals(name.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        }
        screenshot("player-next-channel-names")
    }

    @Test fun volumeDecreaseFollowsHardwareStyleChangeInsteadOfOldUiSnapshot() {
        val audio = rule.activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assumeTrue(!audio.isVolumeFixed && audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) >= 3)
        val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            rule.onNodeWithText(label(R.string.player_more_controls)).performScrollTo().performClick()
            rule.onNodeWithText(label(R.string.player_levels)).performClick()
            val prefix = rule.activity.getString(R.string.player_volume_decrease, 0).substringBefore('0')
            val decrease = rule.onNodeWithContentDescription(prefix, substring = true)
            decrease.performScrollTo().assertIsDisplayed()
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0)
            rule.waitUntil(5_000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 3 }
            decrease.performClick()
            rule.waitUntil(5_000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 2 }
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
        }
    }

    @Test fun moreMenuKeepsAccessibleLevelButtonsOperable() {
        rule.onNodeWithText(label(R.string.player_more_controls)).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.player_levels)).performClick()
        val prefix = rule.activity.getString(R.string.player_brightness_increase, 0).substringBefore('0')
        val increase = rule.onNodeWithContentDescription(prefix, substring = true)
        increase.performScrollTo().assertIsDisplayed()
        val before = increase.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        increase.performClick()
        rule.waitUntil(5_000) {
            increase.fetchSemanticsNode().config[SemanticsProperties.ContentDescription] != before
        }
        screenshot("player-level-controls")
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FakeOriginServer
    private var previousAutoSkip = true

    @Before fun setup() {
        previousAutoSkip = setAutoSkipDeadChannels(
            InstrumentationRegistry.getInstrumentation().targetContext, enabled = false,
        )
        server = FakeOriginServer.startWithChannels(channelCount = 3)
        rule.activityRule.scenario.onActivity { skipOnboarding(it); loadTestPlaylist(it, server) }
        rule.waitForChannelsLoaded(server)
        rule.openChannelViaSearch("Channel 1")
    }

    @After fun cleanup() {
        server.shutdown()
        setAutoSkipDeadChannels(InstrumentationRegistry.getInstrumentation().targetContext, previousAutoSkip)
    }

    @Test fun settingsSearchFindsAndRevealsWifiControl() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        rule.onNodeWithTag(UiTestTags.SETTINGS_SEARCH).performTextInput("Wi-Fi")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText(label(R.string.settings_icon_wifi_only_label)).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription(label(R.string.settings_back_to_overview)).assertIsDisplayed()
        val target = rule.onNode(hasText(label(R.string.settings_icon_wifi_only_label)) and isToggleable())
        rule.waitUntil(5_000) {
            target.fetchSemanticsNode().boundsInRoot.bottom <=
                rule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).fetchSemanticsNode().boundsInRoot.top
        }
        target.assertIsDisplayed()
        val before = target.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        target.performClick()
        rule.waitUntil(5_000) { target.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] != before }
        screenshot("settings-wifi-target")
    }

    @Test fun devicePickerExplainsProtocolsWithoutConnectingToAnyRealTv() {
        rule.onNodeWithContentDescription(label(R.string.player_devices)).performClick()
        rule.onNodeWithText(label(R.string.dlna_sheet_title)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.player_chromecast_cast)).assertIsDisplayed()
        screenshot("player-device-picker")
    }

    @Test fun settingsSearchRoutesGuideAndBufferToTheirTaskPages() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        rule.onNodeWithTag(UiTestTags.SETTINGS_SEARCH).performTextInput("XMLTV")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText(label(R.string.settings_epg_source_label)).performClick()
        rule.onNodeWithContentDescription(label(R.string.settings_back_to_overview)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.settings_epg_source_label)).assertIsDisplayed()
        rule.onNodeWithContentDescription(label(R.string.settings_back_to_overview)).performClick()
        rule.onNodeWithContentDescription(label(R.string.settings_search_clear)).performClick()
        rule.onNodeWithTag(UiTestTags.SETTINGS_SEARCH).performTextInput(label(R.string.settings_buffer_size_label))
        Espresso.closeSoftKeyboard()
        rule.onNode(
            hasText(label(R.string.settings_buffer_size_label)) and !hasTestTag(UiTestTags.SETTINGS_SEARCH),
        ).performClick()
        rule.onNodeWithContentDescription(label(R.string.settings_back_to_overview)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.settings_buffer_size_label)).assertIsDisplayed()
        screenshot("settings-playback")
    }

    @Test fun viewAllSelectsAChannelFromTheActivePlaybackSession() {
        rule.onNodeWithText(label(R.string.player_view_all)).performScrollTo().performClick()
        val channel = rule.onNode(hasText("Channel 3") and hasAnyAncestor(hasTestTag(UiTestTags.PLAYER_CHANNEL_LIST)))
        channel.assertIsDisplayed()
        screenshot("player-channel-picker")
        channel.performClick()
        rule.waitUntil(10_000) {
            var name: String? = null
            rule.activityRule.scenario.onActivity {
                name = ViewModelProvider(it)[PlayerViewModel::class.java].uiState.value.currentChannel?.displayName
            }
            name == "Channel 3"
        }
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
    }

    @Test fun terminalPlaybackErrorKeepsRecoveryActionsReachable() {
        rule.waitUntil(10_000) {
            var fatal = false
            rule.activityRule.scenario.onActivity {
                fatal = ViewModelProvider(it)[PlayerViewModel::class.java].uiState.value.fatalError
            }
            fatal
        }
        rule.onNodeWithText(label(R.string.player_retry)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(label(R.string.player_next)).performScrollTo().assertIsDisplayed()
        screenshot("player-error-actions")
    }

    private fun label(resource: Int): String = rule.activity.getString(resource)

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("ui-polish"))
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
