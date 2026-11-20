package com.uacastplayer.ui.components

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.testing.RequiresComposeTestManifest
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class EntryStaggerTest {
    @get:Rule val rule = createComposeRule()
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private var previousScale = 1f

    @Before fun enableMotion() {
        previousScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @After fun restoreMotion() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
    }

    @Test fun laterRowsDoNotAllocateAnEntryLayer() = assertUnchanged(index = 10)
    @Test fun recycledRowsDoNotAllocateAnEntryLayer() = assertUnchanged(index = 0, alreadyPlayed = true)
    @Test fun removedAnimationsDoNotAllocateAnEntryLayer() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertUnchanged(index = 0)
    }

    @Test fun finishedEntryDropsItsTemporaryLayer() {
        var actual: Modifier? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            actual = Modifier.staggeredEntry(rememberEntryStagger("list"), "row", 0)
            Box(checkNotNull(actual))
        }
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.runOnIdle { assertSame(Modifier, actual) }
    }

    @Test fun reenablingMotionDoesNotReplayAnInterruptedEntry() {
        var actual: Modifier? = null
        val enabled = mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            actual = Modifier.staggeredEntry(rememberEntryStagger("list"), "row", 0, enabled.value)
            Box(checkNotNull(actual))
        }
        rule.runOnIdle { assertNotSame("The initial entry must actually be running", Modifier, actual) }
        changePolicy(enabled, false)
        rule.runOnIdle { assertSame(Modifier, actual) }
        changePolicy(enabled, true)
        rule.runOnIdle { assertSame("Re-enabling motion must not hide already-visible content", Modifier, actual) }
    }

    private fun changePolicy(enabled: androidx.compose.runtime.MutableState<Boolean>, value: Boolean) {
        rule.runOnIdle {
            enabled.value = value
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
        }
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
    }

    private fun assertUnchanged(index: Int, alreadyPlayed: Boolean = false) {
        var actual: Modifier? = null
        rule.setContent {
            val stagger = remember { EntryStagger().also { if (alreadyPlayed) it.markPlayed("row") } }
            actual = Modifier.staggeredEntry(stagger, "row", index)
            Box(checkNotNull(actual))
        }
        rule.runOnIdle { assertSame("Static rows must not retain graphics layers", Modifier, actual) }
    }
}
