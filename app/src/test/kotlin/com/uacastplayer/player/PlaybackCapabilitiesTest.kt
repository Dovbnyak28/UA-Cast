package com.uacastplayer.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCapabilitiesTest {
    private val local = SessionPlayerControls(true, true, true, true, true)

    @Test fun localCommandsRequireBothOwnerPermissionAndTheCorrespondingEngineCommand() {
        val available = setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        val capabilities = local.resolve(available::contains)
        assertTrue(capabilities.canPlayPause)
        assertFalse(capabilities.canPrepare)
        assertTrue(capabilities.canSeek)
        val revoked = local.resolve { false }
        assertFalse(revoked.canPlayPause)
        assertFalse(revoked.canPrepare)
        assertFalse(revoked.canSeek)
    }

    @Test fun remoteOwnershipCannotBeOverriddenByEngineAvailability() {
        val remote = SessionPlayerControls(false, false, false, true, false).resolve { true }
        assertFalse(remote.canPlayPause)
        assertFalse(remote.canPrepare)
        assertFalse(remote.canStop)
        assertFalse(remote.canSeek)
        assertTrue(remote.canGoNext)
        assertFalse(remote.canGoPrevious)
    }

    @Test fun virtualChannelNavigationAndReleaseDoNotRequireEnginePlaylistCommands() {
        val capabilities = local.resolve { false }
        assertTrue(capabilities.canGoNext)
        assertTrue(capabilities.canGoPrevious)
        assertTrue(capabilities.canStop)
    }
}
