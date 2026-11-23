package app.zorik.player.ui

import app.zorik.player.AppViewModel
import app.zorik.player.favorites.FavoriteChannel
import app.zorik.player.favorites.FavoriteKey

import android.os.Build
import android.graphics.Bitmap
import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.zorik.player.MainActivity
import app.zorik.player.R
import app.zorik.player.player.PlayerViewModel
import app.zorik.player.testsupport.FakeOriginServer
import app.zorik.player.testsupport.loadTestPlaylist
import app.zorik.player.testsupport.openChannelViaSearch
import app.zorik.player.testsupport.setAutoSkipDeadChannels
import app.zorik.player.testsupport.skipOnboarding
import app.zorik.player.testsupport.waitForChannelsLoaded
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
    @Test fun lastFavoriteCanBeRestoredWithoutCoveringTheMiniPlayer() {
        verifyFavoriteUndo(recreate = false)
    }

    @Test fun pendingFavoriteUndoSurvivesActivityRecreation() {
        verifyFavoriteUndo(recreate = true)
    }

    private fun verifyFavoriteUndo(recreate: Boolean) {
        var original: FavoriteChannel? = null
        rule.activityRule.scenario.onActivity { activity ->
            val app = ViewModelProvider(activity)[AppViewModel::class.java]
            val channel = app.playlistState.value.channels[1]
            val item = FavoriteChannel(
                FavoriteKey.of(channel), "Undo channel", channel.streamUrl,
                channel.tvgId, channel.groupTitle, addedAtMillis = 42L, userAgent = "Undo agent",
            )
            original = item
            app.favoritesRepository.reorder(listOf(item))
        }
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_favorites)).performClick()
        rule.onNodeWithText(label(R.string.favorites_edit)).performClick()
        rule.onNodeWithContentDescription(label(R.string.favorites_remove_content_description)).performClick()
        rule.onNodeWithText(label(R.string.favorites_remove_content_description)).performClick()
        if (recreate) rule.activityRule.scenario.recreate()
        val undo = rule.onNodeWithText(label(R.string.common_undo)).assertIsDisplayed()
        org.junit.Assert.assertTrue(undo.fetchSemanticsNode().boundsInRoot.bottom <=
            rule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).fetchSemanticsNode().boundsInRoot.top)
        screenshot(if (recreate) "favorite-undo-restored" else "favorite-undo")
        undo.performClick()
        rule.onNodeWithText("Undo channel").assertIsDisplayed()
        rule.activityRule.scenario.onActivity { activity ->
            assertEquals(listOf(original), ViewModelProvider(activity)[AppViewModel::class.java]
                .favorites.value)
        }
    }

    @Test fun settingsHasOneHeadingAndBackReturnsThroughOverviewThenHome() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_favorites)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        rule.onNodeWithText(label(R.string.settings_section_general)).performClick()
        rule.onAllNodesWithTag(UiTestTags.ROOT_TOP_BAR_TITLE).assertCountEquals(1)
        Espresso.pressBack()
        rule.onNodeWithTag(UiTestTags.SETTINGS_SEARCH).assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithContentDescription(label(R.string.nav_home)).assertIsSelected()
        rule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).assertIsDisplayed()
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
        screenshot("navigation-home")
    }

    @Test fun miniPlayerAndDockRemainAdjacentAndTabChangesKeepOnePlayer() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        val mini = rule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).assertIsDisplayed()
        val miniBounds = mini.fetchSemanticsNode().boundsInRoot
        if (rule.onAllNodesWithTag(UiTestTags.NAVIGATION_RAIL).fetchSemanticsNodes().isNotEmpty()) {
            val railBounds = rule.onNodeWithTag(UiTestTags.NAVIGATION_RAIL)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            // Beside a rail the mini player starts after it (the rail keeps its full height).
            org.junit.Assert.assertTrue(railBounds.right <= miniBounds.left + 1f)
        } else {
            val dockBounds = rule.onNodeWithTag(UiTestTags.NAVIGATION_DOCK)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue(miniBounds.bottom <= dockBounds.top + 1f)
            org.junit.Assert.assertTrue(dockBounds.top - miniBounds.bottom <= 1f)
        }
        rule.onNodeWithContentDescription(label(R.string.nav_favorites)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
    }

    @Test fun landscapeNavigationKeepsAllDestinationsReachableBesideTheMiniPlayer() {
        val originalOrientation = rule.activity.requestedOrientation
        try {
            // Collapse the player first. Leaving the expanded player releases its fullscreen
            // orientation request (FullscreenController sets UNSPECIFIED), so a landscape request
            // made before Back is undone by it: on a device that follows the sensor - every CI
            // emulator - the window returns to portrait and the rail is replaced by the dock.
            rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
            rule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).assertIsDisplayed()
            rule.activityRule.scenario.onActivity {
                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            rule.waitUntil(10_000) {
                rule.activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            rule.waitForIdle()
            val miniBounds = rule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val railBounds = rule.onNodeWithTag(UiTestTags.NAVIGATION_RAIL)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            // On a short landscape window (the ~320dp-high CI emulators, more so on API 36 with its
            // taller system bars) four 48dp targets do not fit between the top bar and the mini player,
            // so the rail scrolls (see GlassNavigationRail). What must hold is that every destination
            // can be reached and is never covered by the mini player - not that all four fit at once.
            // The mini player sits to the right of the rail, so "not covered" means no overlap, not
            // "above its top edge".
            org.junit.Assert.assertTrue("Mini player overlaps the rail: $miniBounds vs $railBounds",
                railBounds.right <= miniBounds.left + 1f)
            listOf(R.string.nav_home, R.string.nav_channels, R.string.nav_favorites, R.string.nav_settings)
                .forEach { destination ->
                    val bounds = rule.onNodeWithContentDescription(label(destination))
                        .performScrollTo()
                        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    org.junit.Assert.assertTrue("Navigation destination is clipped or covered: $bounds",
                        bounds.top >= railBounds.top && bounds.bottom <= railBounds.bottom &&
                            !bounds.overlaps(miniBounds))
                }
            rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
            rule.onNodeWithTag(UiTestTags.SETTINGS_SEARCH).assertIsDisplayed()
            rule.onNodeWithContentDescription(label(R.string.nav_settings)).assertIsSelected()
            rule.waitForIdle()
            screenshot("landscape-rail-mini")
        } finally {
            rule.activityRule.scenario.onActivity { it.requestedOrientation = originalOrientation }
        }
    }

    @Test fun systemThemePreferenceSurvivesActivityRecreation() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        rule.onNodeWithText(label(R.string.settings_section_general)).performClick()
        rule.onNodeWithText(label(R.string.settings_theme_label)).performClick()
        rule.onNodeWithText(label(R.string.theme_name_system)).performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText(label(R.string.theme_name_system)).assertIsDisplayed()
        rule.onAllNodesWithTag(UiTestTags.ROOT_TOP_BAR_TITLE).assertCountEquals(1)
        screenshot("settings-system-theme")
    }

    @Test fun homeGuideShortcutOpensTheGuideEditorAndBackReturnsToSettingsOverview() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_home)).performClick()
        rule.onNodeWithContentDescription(label(R.string.home_more_details)).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.home_epg_configure)).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.settings_epg_source_label)).assertIsDisplayed()
        screenshot("home-epg-editor")
        rule.onNodeWithContentDescription(label(R.string.settings_back_to_overview)).performClick()
        rule.onNodeWithTag(UiTestTags.SETTINGS_SEARCH).assertIsDisplayed()
    }

    @Test fun daylightThemeAndReducedEffectsCanBeSelectedFromTheRealSettingsScreen() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        rule.onNodeWithText(label(R.string.settings_section_general)).performClick()
        rule.onNodeWithText(label(R.string.settings_theme_label)).performClick()
        rule.onNodeWithText(label(R.string.theme_name_daylight)).performClick()
        rule.onNodeWithText(label(R.string.theme_name_daylight)).assertIsDisplayed()
        val toggle = rule.onNode(hasText(label(R.string.settings_reduced_effects)) and isToggleable())
        toggle.performScrollTo()
        if (toggle.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] !=
            androidx.compose.ui.state.ToggleableState.On) toggle.performClick()
        assertEquals(androidx.compose.ui.state.ToggleableState.On,
            toggle.fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        screenshot("settings-daylight-reduced-effects")
        rule.runOnUiThread {
            assertEquals(true, androidx.core.view.WindowCompat.getInsetsController(
                rule.activity.window, rule.activity.window.decorView,
            ).isAppearanceLightStatusBars)
        }
    }

    @Test fun supportMakesHelpAvailableWithoutScrollingPastPremium() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        rule.onNodeWithText(label(R.string.settings_page_help_about)).performScrollTo().performClick()
        rule.onNodeWithTag(UiTestTags.SETTINGS_OPEN_HELP_BUTTON).assertIsDisplayed().performClick()
        rule.onNodeWithText(label(R.string.help_q1_title)).assertIsDisplayed()
        screenshot("support-help-first")
    }

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

    @Test fun slidersApplyRealWindowBrightnessAndHardwareVolume() {
        val audio = rule.activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assumeTrue(!audio.isVolumeFixed && audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) > 0)
        val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            rule.onNodeWithText(label(R.string.player_more_controls)).performScrollTo().performClick()
            screenshot("player-actions")
            rule.onNodeWithText(label(R.string.player_levels)).performClick()
            rule.onNodeWithTag("player_brightness_slider").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
            rule.activityRule.scenario.onActivity {
                assertEquals(0.8f, it.window.attributes.screenBrightness, 0.01f)
            }
            rule.onNodeWithTag("player_volume_slider").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(0.6f) }
            val expected = (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.6f).toInt()
            rule.waitUntil(5_000) { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == expected }
            screenshot("player-level-sliders")
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
        }
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

    @Test fun settingsSearchShowsParentalDestinationOnce() {
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        rule.onNodeWithTag(UiTestTags.SETTINGS_SEARCH).performTextInput(label(R.string.settings_section_parental_control))
        Espresso.closeSoftKeyboard()
        val result = hasText(label(R.string.settings_section_parental_control)) and !hasTestTag(UiTestTags.SETTINGS_SEARCH)
        rule.onAllNodes(result).assertCountEquals(1)
        screenshot("settings-parental-search")
        rule.onNode(result).performClick()
        rule.onNodeWithContentDescription(label(R.string.settings_back_to_overview)).assertIsDisplayed()
    }

    @Test fun separateCastAndDlnaControlsOpenOnlyTheRequestedPicker() {
        rule.onNodeWithContentDescription(label(R.string.player_chromecast_cast)).assertIsDisplayed()
        rule.onNodeWithContentDescription(label(R.string.dlna_sheet_title)).performClick()
        rule.onNodeWithText(label(R.string.dlna_sheet_title)).assertIsDisplayed()
        screenshot("player-dlna-picker")
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

    @Test fun channelPickerClearsSearchAndImeSelectionUsesTheSessionIndex() {
        rule.onNodeWithText(label(R.string.player_view_all)).performScrollTo().performClick()
        val search = rule.onNodeWithTag(UiTestTags.PLAYER_CHANNEL_SEARCH)
        search.performTextInput("missing")
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText(label(R.string.player_channels_no_results))).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasContentDescription(label(R.string.channels_clear_search)) and
            hasAnyAncestor(hasTestTag(UiTestTags.PLAYER_CHANNEL_SEARCH))).performClick()
        assertEquals("", search.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        search.performTextInput("Channel 3")
        search.performImeAction()
        search.assertIsNotFocused()
        val resultMatcher = hasText("Channel 3") and hasAnyAncestor(hasTestTag(UiTestTags.PLAYER_CHANNEL_LIST))
        rule.waitUntil(10_000) { rule.onAllNodes(resultMatcher).fetchSemanticsNodes().isNotEmpty() }
        val result = rule.onNode(resultMatcher)
        screenshot("player-channel-picker-search")
        result.performClick()
        rule.waitUntil(10_000) {
            var name: String? = null
            rule.activityRule.scenario.onActivity {
                name = ViewModelProvider(it)[PlayerViewModel::class.java].uiState.value.currentChannel?.displayName
            }
            name == "Channel 3"
        }
        rule.onAllNodesWithTag(UiTestTags.PLAYER_CHANNEL_LIST).assertCountEquals(0)
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
        // PixelCopy waits for a committed Compose frame; semantics alone can precede the draw.
        // Its Window overload exists from API 26 only - below that, idle is the best fence there is.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().recycle()
        }
        // Evidence only, never an assertion. After an orientation change on API 34+ the system can
        // refuse a fresh UiAutomation connection ("UiAutomationService ... already registered")
        // although every check above has passed; losing the picture must not fail the test.
        val bitmap = runCatching { instrumentation.uiAutomation.takeScreenshot() }
            .onFailure { android.util.Log.w("UiPolishEvidence", "No screenshot for $name: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
