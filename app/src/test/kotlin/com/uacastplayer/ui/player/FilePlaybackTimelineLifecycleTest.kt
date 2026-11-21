package com.uacastplayer.ui.player

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.uacastplayer.testing.RequiresComposeTestManifest
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class FilePlaybackTimelineLifecycleTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pausedProjectionStopsItsListenerInBackgroundAndOnDisposal() {
        val owner = TimelineOwner()
        val listeners = mutableSetOf<Player.Listener>()
        var reads = 0
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader, arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getCurrentPosition" -> { reads++; 1000L }
                "getDuration" -> 90_000L
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> null
            }
        } as Player
        val mounted = mutableStateOf(true)
        lateinit var projection: State<FilePlaybackTimeline>
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (mounted.value) {
                    projection = rememberFilePlaybackTimeline(player, "file-a", false)
                    Text(filePlaybackTime(projection.value.position))
                }
            }
        }
        rule.runOnIdle { assertEquals(1, listeners.size); assertEquals(1000L, projection.value.position) }
        val initialReads = reads
        rule.mainClock.advanceTimeBy(5000)
        rule.runOnIdle {
            assertEquals("Paused playback must not start a polling loop", initialReads, reads)
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        rule.runOnIdle { assertEquals(0, listeners.size); owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { assertEquals(1, listeners.size); mounted.value = false }
        rule.runOnIdle { assertEquals(0, listeners.size); owner.registry.currentState = Lifecycle.State.DESTROYED }
    }
}

private class TimelineOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
}
