package com.uacastplayer.parentalcontrol

import com.uacastplayer.playlist.M3uChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerRestoreAccessTest {
    private val channels = listOf("Locked", "Public", "Also locked", "Other").map {
        M3uChannel(it, "http://example.test/$it")
    }
    private val locks = setOf("Locked", "Also locked")

    @Test fun restoredPublicChannelCannotNavigateIntoLockedChannels() {
        val restored = PlayerChannelAccess.forRestore(channels, "Public", locks, { it.displayName }, false, true)
        assertEquals(listOf("Public", "Other"), restored?.channels?.map { it.displayName })
        assertEquals(0, restored?.startIndex)
    }

    @Test fun protectedOrMissingCurrentChannelCannotRestoreItself() {
        for (key in listOf("Locked", "missing")) {
            assertNull(PlayerChannelAccess.forRestore(channels, key, locks, { it.displayName }, false, true))
        }
    }

    @Test fun unknownRestrictionsPreventRestoreEvenWhenTheSnapshotIsEmpty() {
        assertNull(PlayerChannelAccess.forRestore(channels, "Locked", emptySet(), { it.displayName }, false, false))
    }

    @Test fun authorizedSessionRetainsItsFullNavigationList() {
        val restored = PlayerChannelAccess.forRestore(channels, "Public", locks, { it.displayName }, true, true)
        assertEquals(channels, restored?.channels)
        assertEquals(1, restored?.startIndex)
    }
}
