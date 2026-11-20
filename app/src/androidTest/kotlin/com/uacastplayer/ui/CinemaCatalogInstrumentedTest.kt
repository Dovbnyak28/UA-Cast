package com.uacastplayer.ui

import android.os.Build
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.AppViewModel
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.selectAppTheme
import com.uacastplayer.setChannelLayout
import com.uacastplayer.setListDensity
import com.uacastplayer.testsupport.FakeOriginServer
import com.uacastplayer.testsupport.loadTestPlaylist
import com.uacastplayer.testsupport.openChannelViaSearch
import com.uacastplayer.testsupport.setAutoSkipDeadChannels
import com.uacastplayer.testsupport.skipOnboarding
import com.uacastplayer.testsupport.waitForChannelsLoaded
import com.uacastplayer.toggleFavorite
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.favorites.FavoriteChannel
import com.uacastplayer.ui.theme.AppTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual MainActivity routes, a local fixture origin and the existing player owner. */
@RunWith(AndroidJUnit4::class)
class CinemaCatalogInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var origin: FakeOriginServer
    private var autoSkip = true
    private var originalFavorites: List<FavoriteChannel> = emptyList()

    @Before fun setup() {
        val arguments = InstrumentationRegistry.getArguments()
        val configuration = rule.activity.resources.configuration
        println("Cinema V2 device: ${configuration.screenWidthDp}x${configuration.screenHeightDp}dp " +
            "font=${configuration.fontScale} density=${configuration.densityDpi}")
        arguments.getString("expectedWidthDp")?.toInt()?.let {
            assertEquals("The application must use the requested viewport", it, configuration.screenWidthDp)
        }
        arguments.getString("expectedFontScale")?.toFloat()?.let {
            assertEquals("The application must use the actual system font scale", it, configuration.fontScale, 0.01f)
        }
        autoSkip = setAutoSkipDeadChannels(InstrumentationRegistry.getInstrumentation().targetContext, false)
        origin = FakeOriginServer.startWithChannels(60)
        rule.activityRule.scenario.onActivity { activity ->
            skipOnboarding(activity)
            ViewModelProvider(activity)[AppViewModel::class.java].apply {
                selectAppTheme(AppTheme.CINEMA)
                setChannelLayout(ChannelLayout.LIST)
                setListDensity(ListDensity.FULL)
            }
            loadTestPlaylist(activity, origin)
        }
        rule.waitForChannelsLoaded(origin)
        rule.activityRule.scenario.onActivity {
            val app = ViewModelProvider(it)[AppViewModel::class.java]
            originalFavorites = app.favorites.value
            app.favoritesRepository.reorder(emptyList())
            app.playlistState.value.channels.take(3).forEach(app::toggleFavorite)
        }
    }

    @After fun cleanup() {
        if (!::origin.isInitialized) return
        rule.activityRule.scenario.onActivity {
            ViewModelProvider(it)[AppViewModel::class.java].favoritesRepository.reorder(originalFavorites)
        }
        origin.shutdown()
        setAutoSkipDeadChannels(InstrumentationRegistry.getInstrumentation().targetContext, autoSkip)
    }

    @Test fun homeCategoryAndGuideKeepTheSamePlayerAndLeaveLastActionsUncovered() {
        rule.openChannelViaSearch("Channel 1")
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.player_mini_close)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_home)).performClick()
        screenshot("home-idle-cinema")
        verticalCatalog().performScrollToNode(hasText(label(R.string.home_continue_watching_label)))
        rule.onNodeWithText(label(R.string.home_continue_watching_label)).assertIsDisplayed().performClick()
        val engine = rule.runOnIdle { ViewModelProvider(rule.activity)[PlayerViewModel::class.java].player }
        rule.onNodeWithContentDescription(label(R.string.common_back)).performClick()
        rule.onNodeWithContentDescription(label(R.string.nav_home)).performClick()
        screenshot("home-cinema")

        // The category shortcut must navigate directly into that group, not merely select the tab.
        verticalCatalog().performScrollToNode(hasText("Test"))
        rule.onNodeWithText("Test").performClick()
        rule.onNodeWithContentDescription(label(R.string.settings_channel_layout_label)).assertIsDisplayed()
        screenshot("channels-cinema")
        val guide = rule.onNodeWithContentDescription(label(R.string.channels_channel_guide) + ": Channel 1")
        verticalCatalog().performScrollToNode(
            androidx.compose.ui.test.hasContentDescription(label(R.string.channels_channel_guide) + ": Channel 1"))
        guide.assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription(label(R.string.epg_next_day)).assertIsDisplayed()
        Espresso.pressBack()
        assertSame(engine, rule.runOnIdle { ViewModelProvider(rule.activity)[PlayerViewModel::class.java].player })
        assertEquals(1, PlayerViewModel.liveInstanceCountForTest())

        verticalCatalog().performScrollToNode(
            androidx.compose.ui.test.hasContentDescription(label(R.string.channels_channel_guide) + ": Channel 60"))
        val last = rule.onNodeWithContentDescription(label(R.string.channels_channel_guide) + ": Channel 60")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val mini = rule.onNodeWithTag(UiTestTags.MINI_PLAYER_BAR).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The final EPG button must be entirely above the mini player", last.bottom <= mini.top)
        assertTrue("EPG target is at least 48dp", last.height / rule.density.density >= 48f)
        screenshot("channels-last-mini")
    }

    @Test fun everyThemeKeepsHomeAndSourceActionsAvailable() {
        rule.onNodeWithContentDescription(label(R.string.nav_home)).performClick()
        for (theme in AppTheme.entries) {
            rule.activityRule.scenario.onActivity {
                ViewModelProvider(it)[AppViewModel::class.java].selectAppTheme(theme)
            }
            rule.onNode(hasScrollToIndexAction() and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(0)
            rule.onNodeWithContentDescription(label(R.string.home_change_playlist)).assertIsDisplayed()
            rule.onNodeWithContentDescription(label(R.string.home_more_details)).assertIsDisplayed()
            screenshot("home-theme-${theme.name.lowercase()}")
            verticalCatalog().performScrollToNode(hasText(label(R.string.home_view_channels_button)))
            rule.onNodeWithText(label(R.string.home_view_channels_button)).assertIsDisplayed()
        }
    }

    private fun verticalCatalog() = rule.onNode(hasScrollToNodeAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))

    private fun label(resource: Int) = rule.activity.getString(resource)

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("cinema-v2-stage3"))
        check(directory.isDirectory || directory.mkdirs())
        // PixelCopy waits for a committed Compose frame; semantics alone can precede the draw.
        // Its Window overload exists from API 26 only - below that, idle is the best fence there is.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().recycle()
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
