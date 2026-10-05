package com.uacastplayer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.uacastplayer.player.PlayerRequest
import com.uacastplayer.playlist.M3uChannel
import com.uacastplayer.testing.RequiresComposeTestManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class PlayerOpenReadinessTest {
    @get:Rule val rule = createComposeRule()

    @Test fun latestTapWaitsForRestrictionsThenRequiresPinExactlyOnce() {
        val first = PlayerRequest(listOf(M3uChannel("First", "http://example.test/1")), 0)
        val last = PlayerRequest(listOf(M3uChannel("Protected", "http://example.test/2")), 0)
        val pending = mutableStateOf<PlayerRequest?>(first)
        val ready = mutableStateOf(false)
        val opened = mutableListOf<PlayerRequest>()
        var unlock: (() -> Unit)? = null
        var promptCount = 0
        rule.setContent {
            OpenPlayerWhenReady(pending.value, ready.value, { pending.value = null }, { true }, {
                promptCount++
                unlock = it
            }, opened::add)
        }
        rule.runOnIdle {
            assertEquals(0, promptCount)
            assertEquals(emptyList<PlayerRequest>(), opened)
            pending.value = last
        }
        rule.runOnIdle { ready.value = true }
        rule.runOnIdle {
            assertEquals(1, promptCount)
            assertNull(pending.value)
            assertEquals(emptyList<PlayerRequest>(), opened)
            checkNotNull(unlock).invoke()
            assertEquals(listOf(last), opened)
        }
    }

    @Test fun cancelledTapCannotOpenWhenRestrictionsFinishLoading() {
        val pending = mutableStateOf<PlayerRequest?>(PlayerRequest(listOf(M3uChannel("A", "http://example.test/a")), 0))
        val ready = mutableStateOf(false)
        var opened = 0
        rule.setContent {
            OpenPlayerWhenReady(pending.value, ready.value, { pending.value = null }, { false }, { it() }) { opened++ }
        }
        rule.runOnIdle { pending.value = null }
        rule.runOnIdle { ready.value = true }
        rule.runOnIdle { assertEquals(0, opened) }
    }
}
