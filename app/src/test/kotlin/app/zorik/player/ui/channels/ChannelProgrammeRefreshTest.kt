package app.zorik.player.ui.channels

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
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.epg.EpgChannel
import app.zorik.player.epg.EpgData
import app.zorik.player.epg.EpgIndex
import app.zorik.player.epg.EpgProgramme
import app.zorik.player.epg.EpgUiState
import app.zorik.player.favorites.FavoriteKey
import app.zorik.player.icons.IconPrefetchUiState
import app.zorik.player.player.PlayerViewModel
import app.zorik.player.player.SleepTestApplication
import app.zorik.player.playlist.ChannelGroup
import app.zorik.player.playlist.GroupedChannels
import app.zorik.player.playlist.M3uChannel
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.ui.home.HomeContentState
import app.zorik.player.ui.home.HomeScreen
import app.zorik.player.ui.home.HomeSourceState
import app.zorik.player.ui.player.MiniPlayerBar
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
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
            ZorikTheme(AppTheme.CINEMA) {
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
        rule.setContent { ZorikTheme(AppTheme.CINEMA) { MiniPlayerBar(vm, { null }, epg, 0, {}, {}) } }
        awaitProgramme(OLD_PROGRAMME)
        rule.runOnIdle { vm.start(listOf(initial.copy(displayName = "Updated channel", tvgId = "new")), 0) }
        rule.onNodeWithText("Updated channel").assertExists()
        awaitProgramme(NEW_PROGRAMME)
        rule.onNodeWithText(OLD_PROGRAMME).assertDoesNotExist()
    }

    private fun assertGroupRefresh(initial: M3uChannel, updated: M3uChannel) {
        val channel = mutableStateOf(initial)
        rule.setContent {
            ZorikTheme(AppTheme.CINEMA) {
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
