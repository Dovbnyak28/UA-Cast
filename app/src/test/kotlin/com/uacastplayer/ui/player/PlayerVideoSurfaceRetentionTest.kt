package com.uacastplayer.ui.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SleepTestApplication
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = SleepTestApplication::class, qualifiers = "en-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerVideoSurfaceRetentionTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private val mounted = mutableStateOf(true)
    private lateinit var root: View

    @After fun close() {
        rule.runOnIdle { mounted.value = false }
        rule.waitForIdle()
        store.clear()
    }

    @Test fun fullscreenSwitchesRetainThePlayerViewAndTextureUntilTheScreenCloses() {
        val app = ApplicationProvider.getApplicationContext<SleepTestApplication>()
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[PlayerViewModel::class.java]
        rule.setContent {
            root = LocalView.current
            UaCastTheme(AppTheme.CINEMA) {
                if (mounted.value) {
                    PlayerScreen(vm, {}, { false }, {}, { null }, EpgUiState(), IconPrefetchUiState())
                }
            }
        }
        val original = rule.runOnIdle { videoViews(root).single() }
        val inlineWidth = rule.runOnIdle { original.width }
        val texture = rule.runOnIdle { original.videoSurfaceView }
        val player = rule.runOnIdle { vm.player }
        repeat(5) {
            rule.onNodeWithContentDescription("Fullscreen").performClick()
            rule.runOnIdle {
                assertSame("Fullscreen must move the existing native view", original, videoViews(root).single())
                assertTrue("The retained view must adopt fullscreen bounds", original.width > inlineWidth)
                assertSame(texture, original.videoSurfaceView)
                assertSame(player, original.player)
            }
            rule.onNodeWithContentDescription("Exit fullscreen").performClick()
            rule.runOnIdle {
                assertSame("Inline must retain the native view too", original, videoViews(root).single())
                assertEquals(inlineWidth, original.width)
                assertSame(texture, original.videoSurfaceView)
                assertSame(player, original.player)
                assertEquals(1, PlayerViewModel.liveInstanceCountForTest())
            }
        }
        rule.runOnIdle { mounted.value = false }
        rule.runOnIdle {
            assertNull("Real screen removal must detach the player", original.player)
            assertEquals(0, videoViews(root).size)
        }
    }

    private fun videoViews(view: View): List<PlayerView> = when (view) {
        is PlayerView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { videoViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
