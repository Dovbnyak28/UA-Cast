package com.uacastplayer.ui.player

import android.provider.Settings
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.R
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.player.PlayerRequest
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SleepTestApplication
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = SleepTestApplication::class, qualifiers = "en-w360dp-h640dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerHostMotionTest {
    @get:Rule val rule = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<SleepTestApplication>()
    private val store = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
    private val collapsed = mutableStateOf(false)
    private val mounted = mutableStateOf(true)
    private val request = mutableStateOf(PlayerRequest(emptyList(), 0))
    private var previousScale = 1f

    @Before fun enableMotion() {
        previousScale = Settings.Global.getFloat(
            application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        Settings.Global.putFloat(application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        rule.mainClock.autoAdvance = false
    }

    @After fun close() {
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { mounted.value = false }
        rule.waitForIdle()
        store.clear()
        Settings.Global.putFloat(application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
    }

    @Test fun expandingTheSameHostDoesNotReplayItsCompletedEntrance() {
        val vm = mountHost()
        val player = rule.runOnIdle { vm.player }
        val openingLayers = playerLayerCount()
        completeOpening()
        val completedLayers = playerLayerCount()
        assertTrue("The test must detect the real initial entrance", openingLayers > completedLayers)

        repeat(10) {
            changeHost { collapsed.value = true }
            rule.onNodeWithText(application.getString(R.string.app_name)).assertDoesNotExist()
            changeHost { collapsed.value = false }
            assertEquals("Expanding is not a new player opening", completedLayers, playerLayerCount())
            rule.runOnIdle { assertSame("Collapse must not replace ExoPlayer", player, vm.player) }
        }
        rule.runOnIdle { assertEquals(1, PlayerViewModel.liveInstanceCountForTest()) }
    }

    @Test fun collapsingDuringEntranceDoesNotRestartItWhenExpandedLater() {
        mountHost()
        assertTrue(playerLayerCount() > 0)
        changeHost { collapsed.value = true }
        completeOpening()
        changeHost { collapsed.value = false }
        assertEquals("The entrance can complete while its expanded child is absent", 0, playerLayerCount())
    }

    @Test fun fullyClosingAndReopeningCanStartANewEntrance() {
        mountHost()
        completeOpening()
        assertEquals(0, playerLayerCount())
        changeHost { mounted.value = false }
        changeHost { mounted.value = true }
        assertTrue("A new host should still animate", playerLayerCount() > 0)
        completeOpening()
        assertEquals(0, playerLayerCount())
    }

    @Test fun changingTheOpeningKeyCanStillAnimateTheNewRequest() {
        mountHost()
        completeOpening()
        // Empty requests deliberately isolate composition ownership from network/media preparation.
        changeHost { request.value = PlayerRequest(emptyList(), 1) }
        assertTrue("Hoisting must not freeze the request key", playerLayerCount() > 0)
        completeOpening()
        assertEquals(0, playerLayerCount())
    }

    private fun mountHost(): PlayerViewModel {
        val vm = ViewModelProvider(
            store, ViewModelProvider.AndroidViewModelFactory(application),
        )[PlayerViewModel::class.java]
        // No stream, DNS or decoder is needed to exercise the actual host's two composition branches.
        rule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                UaCastTheme(AppTheme.CINEMA) {
                    if (mounted.value) {
                        PlayerHost(
                            request = request.value, collapsed = collapsed.value,
                            onExit = {}, onTapCollapsed = {}, onCollapse = {},
                            resolveIcon = { null }, castArtworkUrl = { null },
                            favoriteActions = PlayerFavoriteActions({ false }, {}),
                            enrichment = PlayerEnrichmentState(EpgUiState(), IconPrefetchUiState()),
                        )
                    }
                }
            }
        }
        return vm
    }

    private fun completeOpening() {
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
    }

    private fun changeHost(change: () -> Unit) {
        rule.runOnIdle {
            change()
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
        }
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
    }

    private fun playerLayerCount(): Int {
        val layerType = Modifier.graphicsLayer {}.javaClass
        val layout = rule.onNodeWithText(application.getString(R.string.app_name)).fetchSemanticsNode().layoutInfo
        return generateSequence(layout) { it.parentInfo }.sumOf { node ->
            node.getModifierInfo().count { it.modifier.javaClass == layerType }
        }
    }
}
