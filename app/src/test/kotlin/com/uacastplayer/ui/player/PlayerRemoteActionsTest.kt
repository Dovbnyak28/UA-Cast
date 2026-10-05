package com.uacastplayer.ui.player

import com.uacastplayer.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRemoteActionsTest {
    @Test fun `remote menu does not expose controls wired to the local player`() {
        listOf(R.string.player_levels, R.string.player_audio_track, R.string.player_subtitle_track,
            R.string.player_quality, R.string.player_aspect_ratio).forEach {
            assertFalse(playerActionAvailableRemotely(it))
        }
        listOf(R.string.player_tv_guide, R.string.player_sleep_timer, R.string.player_view_all).forEach {
            assertTrue(playerActionAvailableRemotely(it))
        }
    }
}
