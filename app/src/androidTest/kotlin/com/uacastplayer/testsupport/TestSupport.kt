package com.uacastplayer.testsupport

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.uacastplayer.AppViewModel
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.acceptTerms
import com.uacastplayer.guidedTourSkip
import com.uacastplayer.loadPlaylistFromUrl
import com.uacastplayer.removePlaylistSource
import com.uacastplayer.selectLanguage
import com.uacastplayer.data.prefs.AppPreferences

internal typealias MainActivityComposeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** Waits for the Activity's first Compose tree, not merely for the test clock to go idle. The
 * Samsung J3's cold start can exceed 15 seconds while ART compiles the large channels screen, so
 * keep this gate generous enough for the slowest supported test handset without changing any
 * functional assertion timeout. */
fun MainActivityComposeRule.awaitComposeHierarchy(timeoutMillis: Long = DEFAULT_COMPOSE_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) {
        runCatching {
            onRoot().fetchSemanticsNode()
            true
        }.getOrDefault(false)
    }
    waitForIdle()
}

/** The same [AppViewModel] instance Compose is using, fetched the same way `by viewModels()`
 * would - both resolve against the Activity's own [androidx.lifecycle.ViewModelStore] via the
 * default class-keyed lookup, so this is never a second, independent instance. */
fun appViewModelOf(activity: MainActivity): AppViewModel = ViewModelProvider(activity)[AppViewModel::class.java]

/** Bypasses every pre-app gate directly via [AppViewModel], the same state a returning user would
 * already have - not by clicking through text that varies by locale and that none of these
 * lifecycle tests are actually about. Re-selecting the current language (rather than a different
 * one) avoids MainActivity's language-change `recreate()`.
 *
 * Both of MainActivity's gates are cleared here, in the order it checks them: language picker, then
 * [com.uacastplayer.ui.legal.TermsScreen]. The guided tour is not a gate - it draws *over* the app
 * rather than in front of it - but it is dismissed anyway, because an overlay that consumes every
 * touch would make every later `performClick` in these tests hit a scrim.
 *
 * The name is kept from when there was a third gate (a three-card onboarding pager, since removed).
 * Missing that one did not fail loudly - the app simply sat on the pager and every later
 * `onNodeWithText` failed with "could not find any node", which reads like a broken assertion
 * rather than a gate that was never passed. It took five instrumented tests down unnoticed, because
 * these do not run against this helper in CI. */
fun skipOnboarding(activity: MainActivity) {
    val viewModel = appViewModelOf(activity)
    viewModel.selectLanguage(viewModel.uiState.value.language)
    viewModel.acceptTerms()
    viewModel.guidedTourSkip()
}

/**
 * Turns the "skip dead channels" setting off (or back on), returning what it was before so a test
 * can restore it.
 *
 * [FakeOriginServer] deliberately serves bytes no decoder accepts - these tests are about the
 * player's *lifecycle*, not about decoding - so every channel fails fatally within a second or two.
 * With auto-skip on, that is not a static failure to assert against: `giveUpOnCurrentChannel()`
 * marks the channel dead and advances to the next playable one, so a test that opened "Channel 1"
 * finds itself on "Channel 3", and once all three are exhausted there is no player left to inspect
 * at all. Switching it off keeps the player parked on the channel the test opened, which is the
 * precondition every assertion here was written against.
 *
 * Goes through [AppPreferences] rather than the Activity's view model because [PlayerViewModel]
 * reads the flag once, when it is constructed - so this has to be in place *before* the player is
 * opened - and because restoring it in `@After` must work whether or not the Activity is still
 * alive. It is a real, persisted user setting on the device the suite runs against, hence the
 * restore rather than a blind write.
 */
fun setAutoSkipDeadChannels(context: Context, enabled: Boolean): Boolean {
    val preferences = AppPreferences(context.applicationContext)
    val previous = preferences.autoSkipDeadEnabled
    preferences.autoSkipDeadEnabled = enabled
    return previous
}

/** Starts loading channels from [server] the same way AddPlaylistScreen's URL field would,
 * without depending on that screen's UI. */
fun loadTestPlaylist(activity: MainActivity, server: FakeOriginServer) {
    val viewModel = appViewModelOf(activity)
    // Instrumentation installs with -r, and each test method gets a fresh Activity but the same
    // app data. Player suites therefore accumulated one ephemeral localhost source per method
    // until PlaylistSourcePolicy's production limit rejected every later setup. Clear the
    // source set before adding this test's isolated server. Sequencing the load after the final
    // wholesale write is essential: otherwise the next Activity can restore an older asynchronous
    // removal write. This callback must stay asynchronous because `onActivity` runs on Android's
    // main thread; blocking it while a viewModelScope job tries to resume there would deadlock.
    viewModel.playlistSources.value.toList().forEach { source ->
        viewModel.removePlaylistSource(source.id)
    }
    viewModel.playlistController.applyImportedSources(emptyList()).invokeOnCompletion { failure ->
        if (failure == null) {
            activity.runOnUiThread { viewModel.loadPlaylistFromUrl(server.playlistUrl()) }
        }
    }
}

/** Waits (polling [AndroidComposeTestRule.waitUntil]) until [FakeOriginServer]'s playlist has been
 * parsed and is visible in [AppViewModel.playlistState]. */
fun MainActivityComposeRule.waitForChannelsLoaded(
    server: FakeOriginServer,
    timeoutMillis: Long = 10_000,
) {
    try {
        waitUntil(timeoutMillis) { appViewModelOf(activity).playlistState.value.hasChannels }
    } catch (timeout: ComposeTimeoutException) {
        val viewModel = appViewModelOf(activity)
        val state = viewModel.playlistState.value
        throw AssertionError(
            "Playlist setup timed out: loading=${state.isLoading}, hasChannels=${state.hasChannels}, " +
                "error=${state.error}, sourceCount=${viewModel.playlistSources.value.size}, " +
                "activeSource=${viewModel.activePlaylistSourceId.value != null}, " +
                "originRequests=${server.playlistRequestCount}, " +
                "responses=${server.completedResponseCount}, failures=${server.failedResponseCount}, " +
                "activeSockets=${server.activeSocketCount}",
            timeout,
        )
    }
}

/**
 * Clicks the Channels entry in the bottom tab bar.
 *
 * Matching on the label alone is not enough: once a playlist is loaded, the Home screen shows an
 * active-playlist card whose *merged* semantics text includes the stat label "Channels" too, so a
 * plain `onNodeWithText(nav_channels)` finds two nodes and fails with "Expected exactly '1' node
 * but found '2'". Narrowing by [Role.Tab] - which [com.uacastplayer.ui.components.GlassTabBar]
 * already sets - picks the tab without needing a test tag or a production change, and keeps
 * working if the card's wording changes.
 */
fun MainActivityComposeRule.openChannelsTab() {
    // A state update (e.g. playlist load) can complete before the corresponding tab has been
    // composed on a cold device. Synchronize the first semantics query with that composition.
    awaitComposeHierarchy()
    onNode(
        hasText(activity.getString(R.string.nav_channels)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    ).performClick()
}

/**
 * Drives the real Channels tab -> whole-playlist search -> tap result flow to open the player on
 * [channelName], exactly as a user would (search is the only channel-list entry point that
 * doesn't require first tapping into a specific group card).
 *
 * Two things make the final tap harder than `onNodeWithText(channelName)`:
 *
 * 1. The search field itself reports the typed query as its own semantics text, so the query and
 *    the result row both match the name. Excluding nodes with a set-text action leaves the row.
 * 2. The query is debounced (see `rememberDebounced`, 200ms) and the filtering runs off the
 *    composition thread, so the row does not exist yet when [waitForIdle] returns - `waitForIdle`
 *    waits for composition to settle, not for a real-time `delay`. Without the explicit wait below
 *    the only match is the text field, clicking it does nothing, and the failure surfaces much
 *    later as "player never opened" rather than as "the result was not there yet".
 */
fun MainActivityComposeRule.openChannelViaSearch(channelName: String) {
    openChannelsTab()
    waitForIdle()
    onNodeWithText(activity.getString(R.string.channels_search_all_hint)).performTextInput(channelName)
    tapChannelRow(channelName)
    // The search field keeps focus behind the player, so the soft keyboard is still up once this
    // returns. That matters to any test that presses system back afterwards: Android gives the back
    // key to the IME first, which swallows it to dismiss itself, and the app's own BackHandler never
    // runs. The symptom is a back press that appears to do nothing at all - the player stays
    // expanded and a test looking for the collapsed mini bar fails with "could not find any node",
    // which reads like the mini bar being broken rather than like the key never arriving.
    Espresso.closeSoftKeyboard()
    waitForIdle()
}

/**
 * Taps the channel-list row for [channelName], waiting for it to appear first.
 *
 * Not `onNodeWithText(channelName)`, for two reasons - and both also apply when *re*-opening a
 * channel later in a test, since the search query stays in the field across an open/close cycle:
 *
 * 1. The search field reports the typed query as its own semantics text, so the query and the
 *    result row both match the name and the lookup fails with "found '2' nodes". Excluding nodes
 *    that accept a set-text action leaves the row.
 * 2. The query is debounced (`rememberDebounced`, 200ms) and filtering runs off the composition
 *    thread, so the row does not exist yet when [waitForIdle] returns - that waits for composition
 *    to settle, not for a real-time `delay`.
 */
fun MainActivityComposeRule.tapChannelRow(channelName: String) {
    val row = hasText(channelName) and hasSetTextAction().not() and hasClickAction()
    waitUntil(SEARCH_RESULT_TIMEOUT_MILLIS) { onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
    onNode(row).performClick()
    waitForIdle()
}

/** Generous next to the 200ms search debounce - this is a "did it ever appear" bound, not a
 * performance assertion, and a cold first search also pays for the initial filter pass. */
private const val SEARCH_RESULT_TIMEOUT_MILLIS = 5_000L
private const val DEFAULT_COMPOSE_TIMEOUT_MILLIS = 30_000L
