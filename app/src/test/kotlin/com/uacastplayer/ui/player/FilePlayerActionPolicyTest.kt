package com.uacastplayer.ui.player

import com.uacastplayer.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePlayerActionPolicyTest {
    @Test fun fileMenusNeverOfferChannelOnlyActions() {
        listOf(R.string.player_tv_guide, R.string.player_view_all, R.string.player_previous_channel).forEach {
            assertFalse(playerActionVisible(it, false, true))
            assertFalse(playerActionVisible(it, true, true))
        }
        assertTrue(playerActionVisible(R.string.player_audio_track, false, true))
        assertTrue(playerActionVisible(R.string.player_sleep_timer, true, true))
    }

    @Test fun channelAndRemoteAvailabilityRetainTheirExistingContract() {
        assertTrue(playerActionVisible(R.string.player_tv_guide, true, false))
        assertTrue(playerActionVisible(R.string.player_view_all, true, false))
        assertFalse(playerActionVisible(R.string.player_audio_track, true, false))
    }
}
