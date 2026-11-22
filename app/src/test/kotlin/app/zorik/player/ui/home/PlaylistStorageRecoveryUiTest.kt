package app.zorik.player.ui.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.zorik.player.R
import app.zorik.player.core.settings.ChannelLayout
import app.zorik.player.core.settings.ListDensity
import app.zorik.player.epg.EpgUiState
import app.zorik.player.icons.IconPrefetchUiState
import app.zorik.player.playlist.PlaylistError
import app.zorik.player.playlist.PlaylistUiState
import app.zorik.player.testing.RequiresComposeTestManifest
import app.zorik.player.ui.channels.ChannelsScreen
import app.zorik.player.ui.theme.AppTheme
import app.zorik.player.ui.theme.ZorikTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "uk-w320dp-h891dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class PlaylistStorageRecoveryUiTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var retries = 0
    private var additions = 0

    @Test fun homeOffersRecoveryOfStoredDataInsteadOfAskingForANewSource() {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            HomeScreen(
                HomeContentState(PlaylistUiState(error = PlaylistError.Storage), EpgUiState(),
                    IconPrefetchUiState(), emptyList(), null),
                HomeSourceState(emptyList(), null, {}, {}, { additions++ }, { retries++ }),
                { null }, { _, _ -> }, {},
            )
        } }
        rule.onNodeWithText(context.getString(R.string.common_retry)).assertIsDisplayed().performClick()
        assertEquals(1, retries)
        assertEquals(0, additions)
    }

    @Test fun channelsOffersRecoveryOfStoredDataEvenWithoutASourceUrl() {
        channels(PlaylistError.Storage)
        rule.onNodeWithText(context.getString(R.string.common_retry)).assertIsDisplayed().performClick()
        assertEquals(1, retries)
        assertEquals(0, additions)
    }

    @Test fun anUnreadableExternalFileStillOffersReselection() {
        channels(PlaylistError.Network)
        rule.onNodeWithText(context.getString(R.string.home_add_playlist_button)).assertIsDisplayed().performClick()
        assertEquals(0, retries)
        assertEquals(1, additions)
    }

    private fun channels(error: PlaylistError) {
        rule.setContent { ZorikTheme(AppTheme.CINEMA) {
            ChannelsScreen(
                playlistState = PlaylistUiState(error = error), onChannelSelected = { _, _ -> },
                epgState = EpgUiState(), iconPrefetchState = IconPrefetchUiState(), resolveIcon = { null },
                density = ListDensity.MINIMAL, layout = ChannelLayout.LIST, onChannelLayoutSelected = {},
                isFavorite = { false }, onToggleFavorite = {}, isChannelLocked = { false },
                onLockChannel = {}, onUnlockChannel = {}, onRefreshPlaylist = { retries++ },
                showIconTierBanner = false, onEnableIcons = {}, onDismissIconTierBanner = {},
                pinnedGroupKeys = emptySet(), hiddenGroupKeys = emptySet(), onPinGroup = {}, onHideGroup = {},
                onClearGroupOverride = {}, onOpenAddPlaylist = { additions++ },
            )
        } }
    }
}
