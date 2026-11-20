package com.uacastplayer.ui.tv

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.uacastplayer.R
import com.uacastplayer.favorites.FavoriteKey
import com.uacastplayer.core.remote.RemoteCommand
import com.uacastplayer.playlist.ChannelGroup
import com.uacastplayer.playlist.GroupedChannels
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.playlist.PlaylistUiState
import com.uacastplayer.premium.Entitlements
import com.uacastplayer.premium.PremiumSectionState
import com.uacastplayer.remote.dispatchTvRemote
import com.uacastplayer.ui.components.SecondaryButton
import com.uacastplayer.ui.premium.PremiumBottomSheet
import com.uacastplayer.ui.settings.HiddenGroupsSheet
import com.uacastplayer.ui.settings.ParentalControlSection
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.uacastplayer.testing.RequiresComposeTestManifest
import org.junit.experimental.categories.Category
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fixtures never alter app preferences: only the production sheets and Activity dispatch are used. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w960dp-h540dp-television-xhdpi")
@Category(RequiresComposeTestManifest::class)
class TvEmptyModalInputTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var registry: TvInputRegistry
    private val showing = mutableStateOf(true)
    private var underlyingBack = 0
    private var dismissed = 0
    private val channel = M3uChannel("Fixture channel", "https://example.test/live")
    private val grouped = GroupedChannels(ChannelGroup.Custom("Fixture group"), listOf(channel))

    @Test fun hiddenGroupsRetainsInputWhenTheLastDisplayedGroupIsRestored() {
        val groups = mutableStateOf(listOf(grouped))
        render { dismiss -> HiddenGroupsSheet(groups.value, { groups.value = emptyList() }, dismiss) }
        assertRegistered()
        rule.onNodeWithText(rule.activity.getString(R.string.settings_hidden_groups_restore)).performClick()
        checkBackClosesModal(expectedDismissals = 1)
    }

    @Test fun lockedChannelsRetainsInputWhenTheLastChannelIsUnlocked() {
        val keys = mutableStateOf(setOf(FavoriteKey.of(channel)))
        render { _ -> ParentalControlSection(PlaylistUiState(groups = listOf(grouped)), keys.value,
            true, { true }, {}, { keys.value = emptySet() }, { it() }) }
        rule.onNodeWithText(rule.activity.getString(R.string.parental_control_manage_locked, 1)).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(rule.activity.getString(
            R.string.parental_control_unlock_action)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(rule.activity.getString(R.string.parental_control_unlock_action)).performClick()
        // Filtering is owned by produceState/withPlaylistCpu, not the UI test's virtual frame clock.
        rule.waitUntil(10_000) { rule.onAllNodesWithText(channel.displayName).fetchSemanticsNodes().isEmpty() }
        checkBackClosesModal(expectedDismissals = 0)
    }

    @Test fun premiumWithoutStoreProductsStillOwnsItsModalWindow() {
        val premium = PremiumSectionState(Entitlements.FREE, emptyList(),
            { error("Must not purchase") }, { error("Must not restore") })
        render { dismiss -> PremiumBottomSheet(premium, dismiss) }
        checkBackClosesModal(expectedDismissals = 1)
    }

    private fun render(content: @Composable (() -> Unit) -> Unit) {
        rule.setContent {
            UaCastTheme(AppTheme.CINEMA) {
                val input = remember { TvInputRegistry() }.also { registry = it }
                TvPresentation(true, input) {
                    BackHandler { underlyingBack++ }
                    Box(Modifier.fillMaxSize()) { SecondaryButton("Underlying action", {}) }
                    if (showing.value) Column { content { dismissed++; showing.value = false } }
                }
            }
        }
    }

    private fun assertRegistered() = rule.runOnIdle {
        assertNotNull("An empty modal must still intercept the phone's keys",
            registry.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN)))
    }

    private fun checkBackClosesModal(expectedDismissals: Int) {
        assertRegistered()
        rule.runOnIdle { rule.activity.dispatchTvRemote(RemoteCommand.BACK, registry::dispatchToDialog) }
        rule.waitForIdle()
        assertEquals(0, underlyingBack)
        assertEquals(expectedDismissals, dismissed)
        rule.runOnIdle {
            assertNull(registry.dispatchToDialog(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN)))
        }
    }
}
