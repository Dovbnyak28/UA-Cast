package com.uacastplayer.ui.channels

import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.settings.ChannelLayout
import com.uacastplayer.core.settings.ListDensity
import com.uacastplayer.epg.EpgChannel
import com.uacastplayer.epg.EpgData
import com.uacastplayer.epg.EpgIndex
import com.uacastplayer.epg.EpgProgramme
import com.uacastplayer.epg.EpgUiState
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.icons.IconPrefetchUiState
import com.uacastplayer.player.PlayerViewModel
import com.uacastplayer.player.SleepTestApplication
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.ui.home.HomeContentState
import com.uacastplayer.ui.home.HomeScreen
import com.uacastplayer.ui.home.HomeSourceState
import com.uacastplayer.ui.player.MiniPlayerBar
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = SleepTestApplication::class, qualifiers = "en-w411dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelProgrammeRefreshTest {
    @get:Rule val rule = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<SleepTestApplication>()
    private val store = ViewModelStore()
    private var previousScale = 1f
    private val epg = EpgUiState(
        data = EpgData(
            EpgIndex(listOf(
                EpgChannel("old", listOf("Old channel"), null),
                EpgChannel("new", listOf("New channel"), null),
            )),
            mapOf(
                "old" to listOf(EpgProgramme("old", 0, 10_000, OLD_PROGRAMME)),
                "new" to listOf(EpgProgramme("new", 0, 10_000, NEW_PROGRAMME)),
            ),
        ),
        nowMillis = 5_000,
    )

    @Before fun disableDecorativeMotion() {
        previousScale = Settings.Global.getFloat(
            application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        Settings.Global.putFloat(application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After fun close() {
        store.clear()
        Settings.Global.putFloat(application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
    }

    @Test fun groupRowRefreshesWhenTvgIdChangesAtTheSameUrl() = assertGroupRefresh(
        M3uChannel("Channel", STREAM, tvgId = "old"),
        M3uChannel("Updated channel", STREAM, tvgId = "new"),
    )

    @Test fun groupRowRefreshesWhenTvgNameChangesAtTheSameUrl() = assertGroupRefresh(
        M3uChannel("Channel", STREAM, tvgName = "Old channel"),
        M3uChannel("Updated channel", STREAM, tvgName = "New channel"),
    )

    @Test fun groupRowRefreshesWhenDisplayNameChangesAtTheSameUrl() = assertGroupRefresh(
        M3uChannel("Old channel", STREAM), M3uChannel("New channel", STREAM),
    )

    @Test fun continueWatchingRefreshesWhenPlaylistMetadataChanges() {
        val initial = M3uChannel("Channel", STREAM, tvgName = "Old channel")
        val channel = mutableStateOf(initial)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                HomeScreen(
                    HomeContentState(
                        PlaylistUiState(channels = listOf(channel.value)), epg, IconPrefetchUiState(),
                        emptyList(), FavoriteKey.of(initial),
                    ),
                    HomeSourceState(emptyList(), null, {}, {}, {}, {}), { null }, { _, _ -> }, {},
                )
            }
        }
        awaitProgramme(OLD_PROGRAMME)
        rule.runOnIdle {
            channel.value = initial.copy(tvgName = "New channel")
            Snapshot.sendApplyNotifications()
        }
        awaitProgramme(NEW_PROGRAMME)
        rule.onNodeWithText(OLD_PROGRAMME).assertDoesNotExist()
    }

    @Test fun miniPlayerRefreshesWhenTheChannelMetadataChanges() {
        val vm = ViewModelProvider(
            store, ViewModelProvider.AndroidViewModelFactory(application),
        )[PlayerViewModel::class.java]
        val initial = M3uChannel("Channel", STREAM, tvgId = "old")
        vm.start(listOf(initial), 0)
        rule.setContent { UaCastTheme(AppTheme.CINEMA) { MiniPlayerBar(vm, { null }, epg, 0, {}, {}) } }
        awaitProgramme(OLD_PROGRAMME)
        rule.runOnIdle { vm.start(listOf(initial.copy(displayName = "Updated channel", tvgId = "new")), 0) }
        rule.onNodeWithText("Updated channel").assertExists()
        awaitProgramme(NEW_PROGRAMME)
        rule.onNodeWithText(OLD_PROGRAMME).assertDoesNotExist()
    }

    private fun assertGroupRefresh(initial: M3uChannel, updated: M3uChannel) {
        val channel = mutableStateOf(initial)
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                SingleGroupChannelList(
                    GroupedChannels(ChannelGroup.Custom("Group"), listOf(channel.value)),
                    epg, 0, { null }, ListDensity.FULL, ChannelLayout.LIST, {}, { false }, {},
                    { false }, {}, {}, {},
                )
            }
        }
        awaitProgramme(OLD_PROGRAMME)
        rule.runOnIdle { channel.value = updated; Snapshot.sendApplyNotifications() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(updated.displayName).fetchSemanticsNodes().isNotEmpty() }
        awaitProgramme(NEW_PROGRAMME)
        rule.onNodeWithText(OLD_PROGRAMME).assertDoesNotExist()
    }

    private fun awaitProgramme(title: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val STREAM = "http://127.0.0.1:9/unavailable"
        const val OLD_PROGRAMME = "Old programme"
        const val NEW_PROGRAMME = "New programme"
    }
}
