package com.uacastplayer.testsupport

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.uacastplayer.AppViewModel
import com.uacastplayer.MainActivity
import com.uacastplayer.R

private typealias MainActivityComposeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** The same [AppViewModel] instance Compose is using, fetched the same way `by viewModels()`
 * would - both resolve against the Activity's own [androidx.lifecycle.ViewModelStore] via the
 * default class-keyed lookup, so this is never a second, independent instance. */
fun appViewModelOf(activity: MainActivity): AppViewModel = ViewModelProvider(activity)[AppViewModel::class.java]

/** Bypasses the language-picker/terms gates directly via [AppViewModel], the same state a
 * returning user would already have - not by clicking through onboarding text that varies by
 * locale and that none of these lifecycle tests are actually about. Re-selecting the current
 * language (rather than a different one) avoids MainActivity's language-change `recreate()`. */
fun skipOnboarding(activity: MainActivity) {
    val viewModel = appViewModelOf(activity)
    viewModel.selectLanguage(viewModel.uiState.value.language)
    viewModel.acceptTerms()
}

/** Starts loading channels from [server] the same way AddPlaylistScreen's URL field would,
 * without depending on that screen's UI. */
fun loadTestPlaylist(activity: MainActivity, server: FakeOriginServer) {
    appViewModelOf(activity).loadPlaylistFromUrl(server.playlistUrl())
}

/** Waits (polling [AndroidComposeTestRule.waitUntil]) until [FakeOriginServer]'s playlist has been
 * parsed and is visible in [AppViewModel.playlistState]. */
fun MainActivityComposeRule.waitForChannelsLoaded(timeoutMillis: Long = 10_000) {
    waitUntil(timeoutMillis) { appViewModelOf(activity).playlistState.value.hasChannels }
}

/** Drives the real Channels tab -> whole-playlist search -> tap result flow to open the player on
 * [channelName], exactly as a user would (search is the only channel-list entry point that
 * doesn't require first tapping into a specific group card). */
fun MainActivityComposeRule.openChannelViaSearch(channelName: String) {
    onNodeWithText(activity.getString(R.string.nav_channels)).performClick()
    waitForIdle()
    onNodeWithText(activity.getString(R.string.channels_search_all_hint)).performTextInput(channelName)
    waitForIdle()
    onNodeWithText(channelName).performClick()
    waitForIdle()
}
