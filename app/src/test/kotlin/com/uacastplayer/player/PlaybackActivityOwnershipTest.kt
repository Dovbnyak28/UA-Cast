package com.uacastplayer.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackActivityOwnershipTest {
    @Test fun `remote owner publishes end after the player owner is gone`() = runTest {
        val applicationScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val remote = MutableStateFlow(true)
        try {
            PlaybackActivity.observeRemote(applicationScope, remote)
            PlaybackActivity.setActive(true)
            // onCleared releases only the player's contribution; no new player is created.
            PlaybackActivity.setActive(false)
            assertTrue(PlaybackActivity.isActive.value)
            remote.value = false
            assertFalse(PlaybackActivity.isActive.value)
        } finally {
            applicationScope.cancel()
            PlaybackActivity.setActive(false)
        }
    }

    @Test fun `ending or cancelling one remote owner cannot release another owner`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            PlaybackActivity.setActive(false)
            val a = PlaybackActivity.observeRemote(scope, MutableStateFlow(true))
            val b = PlaybackActivity.observeRemote(scope, MutableStateFlow(true))
            a.cancel()
            assertTrue(PlaybackActivity.isActive.value)
            b.cancel()
            assertFalse(PlaybackActivity.isActive.value)
        } finally {
            scope.cancel()
        }
    }
}
