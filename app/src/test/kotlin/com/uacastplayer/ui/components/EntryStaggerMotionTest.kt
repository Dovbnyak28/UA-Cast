package com.uacastplayer.ui.components

import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.testing.RequiresComposeTestManifest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class EntryStaggerMotionTest {
    @get:Rule val rule = createComposeRule()
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private var activeResolver: ContentResolver? = null
    private var previousScale = 1f
    private var actual: Modifier? = null
    private val base = Modifier.testTag("row")
    private val entryLayers = mutableListOf<Boolean>()

    @Before fun enableMotion() {
        previousScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        rule.mainClock.autoAdvance = false
    }

    @After fun restoreMotion() {
        rule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
    }

    @Test fun completedEntryDoesNotReplayAfterPolicyToggle() {
        val enabled = mutableStateOf(true)
        mountEntry(enabled)
        rule.runOnIdle { assertNotSame(base, actual) }
        completeEntry()
        rule.runOnIdle { assertSame(base, actual) }
        repeat(10) {
            change { enabled.value = false }
            change { enabled.value = true }
            rule.runOnIdle { assertSame("A completed row is not newly arriving", base, actual) }
        }
    }

    @Test fun initiallyStaticEntryDoesNotHideWhenMotionIsEnabled() {
        val enabled = mutableStateOf(false)
        mountEntry(enabled)
        rule.runOnIdle { assertSame(base, actual) }
        change { enabled.value = true }
        rule.runOnIdle { assertSame("An already-visible row must remain static", base, actual) }
    }

    @Test fun initiallyStaticEntryRemainsSeenWhenItsLazyChildIsRecycled() {
        val enabled = mutableStateOf(false)
        val mounted = mutableStateOf(true)
        mountEntry(enabled, mounted)
        rule.runOnIdle { assertSame(base, actual) }
        change { mounted.value = false }
        change { enabled.value = true }
        change { mounted.value = true }
        rule.runOnIdle { assertSame("The container must remember statically displayed rows", base, actual) }
    }

    @Test fun androidMotionSettingDoesNotHideAnInitiallyStaticEntry() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val epoch = mutableStateOf("first")
        mountEntry(epoch = epoch)
        rule.runOnIdle { assertSame(base, actual) }
        change {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            checkNotNull(activeResolver).notifyChange(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null,
            )
        }
        rule.runOnIdle { assertSame("The observer path must preserve visible content", base, actual) }
        rule.runOnIdle { assertTrue("Even a brief replay must be detected", entryLayers.none { it }) }
        change { epoch.value = "second" }
        rule.runOnIdle { assertTrue("The test must receive the enabled Android policy", entryLayers.any { it }) }
    }

    @Test fun aNewListEpochCanStillAnimateAfterThePreviousStaticEntry() {
        val enabled = mutableStateOf(false)
        val epoch = mutableStateOf("first")
        mountEntry(enabled, epoch = epoch)
        change { enabled.value = true }
        rule.runOnIdle { assertSame(base, actual) }
        change { epoch.value = "second" }
        rule.runOnIdle { assertNotSame("New content must still have its own entrance", base, actual) }
        completeEntry()
        rule.runOnIdle { assertSame(base, actual) }
    }

    @Test fun interruptingAndReenablingEntryDoesNotReplaceItsNativeChild() {
        val enabled = mutableStateOf(true)
        var created = 0
        var released = 0
        rule.setContent {
            actual = base.staggeredEntry(rememberEntryStagger("list"), "row", 0, enabled.value)
            Box(checkNotNull(actual)) {
                AndroidView(
                    factory = { View(it).also { created++ } },
                    onReset = null,
                    onRelease = { released++ },
                )
            }
        }
        rule.runOnIdle { assertNotSame(base, actual) }
        change { enabled.value = false }
        change { enabled.value = true }
        rule.runOnIdle {
            assertSame(base, actual)
            assertEquals("Changing the entry layer must not replace the child", 1, created)
            assertEquals("A mounted child must not be released", 0, released)
        }
    }

    private fun mountEntry(
        enabled: MutableState<Boolean>? = null,
        mounted: MutableState<Boolean> = mutableStateOf(true),
        epoch: MutableState<String> = mutableStateOf("list"),
    ) {
        rule.setContent {
            val stagger = rememberEntryStagger(epoch.value)
            if (mounted.value) {
                activeResolver = LocalContext.current.contentResolver
                actual = base.staggeredEntry(stagger, "row", 0, enabled?.value)
                entryLayers += actual !== base
                Box(checkNotNull(actual))
            }
        }
    }

    private fun change(action: () -> Unit) {
        rule.runOnIdle {
            action()
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
        }
        // Stay below the existing 220ms entrance so a replay cannot finish unnoticed.
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
    }

    private fun completeEntry() {
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
    }
}
