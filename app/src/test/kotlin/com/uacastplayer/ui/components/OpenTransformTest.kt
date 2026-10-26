package com.uacastplayer.ui.components

import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
class OpenTransformTest {
    @get:Rule val rule = createComposeRule()
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private var activeResolver: ContentResolver? = null
    private var previousScale = 1f

    @Before fun enableMotion() {
        previousScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        rule.mainClock.autoAdvance = false
    }

    @After fun restoreMotion() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
    }

    @Test fun removedAnimationsUseTheStaticPathImmediately() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var actual: Modifier? = null
        rule.setContent { actual = Modifier.openingModifier(); Box(checkNotNull(actual)) }
        rule.runOnIdle { assertSame(Modifier, actual) }
    }

    @Test fun staticPathPreservesTheCallerModifier() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val base = Modifier.testTag("surface")
        var actual: Modifier? = null
        rule.setContent { actual = base.openingModifier(); Box(checkNotNull(actual)) }
        rule.runOnIdle { assertSame(base, actual) }
    }

    @Test fun enabledOpeningUsesATemporaryLayer() {
        var actual: Modifier? = null
        rule.setContent { actual = Modifier.openingModifier(); Box(checkNotNull(actual)) }
        rule.runOnIdle { assertNotSame(Modifier, actual) }
    }

    @Test fun completedOpeningDropsItsLayer() {
        var actual: Modifier? = null
        rule.setContent { actual = Modifier.openingModifier(); Box(checkNotNull(actual)) }
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.runOnIdle { assertSame(Modifier, actual) }
    }

    @Test fun unrelatedRecompositionDoesNotReplayCompletedOpening() {
        val label = mutableStateOf("first")
        var actual: Modifier? = null
        rule.setContent { actual = Modifier.openingModifier(); Box(checkNotNull(actual).testTag(label.value)) }
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.runOnIdle { label.value = "updated"; shadowOf(Looper.getMainLooper()).idle() }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertSame(Modifier, actual) }
    }

    @Test fun aNewRequestCanAnimateAfterThePreviousOpeningFinished() {
        val key = mutableStateOf("first")
        val openings = mutableListOf<Pair<String, Boolean>>()
        var actual: Modifier? = null
        rule.setContent {
            actual = Modifier.openingModifier(key = key.value)
            openings += key.value to (actual !== Modifier)
            Box(checkNotNull(actual))
        }
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.runOnIdle {
            assertSame(Modifier, actual)
            key.value = "second"
            shadowOf(Looper.getMainLooper()).idle()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.runOnIdle {
            assertTrue("New request must have its own temporary layer: $openings", "second" to true in openings)
            assertSame(Modifier, actual)
        }
    }

    @Test fun disablingMotionDuringOpeningMakesTheSurfaceStatic() {
        var actual: Modifier? = null
        rule.setContent { actual = Modifier.openingModifier(); Box(checkNotNull(actual)) }
        rule.runOnIdle { assertNotSame(Modifier, actual) }
        changeMotionScale(0f)
        rule.runOnIdle { assertSame(Modifier, actual) }
    }

    @Test fun reenablingMotionDoesNotReplayInterruptedOpening() {
        var actual: Modifier? = null
        rule.setContent { actual = Modifier.openingModifier(); Box(checkNotNull(actual)) }
        changeMotionScale(0f)
        rule.runOnIdle { assertSame(Modifier, actual) }
        changeMotionScale(1f)
        rule.runOnIdle { assertSame(Modifier, actual) }
    }

    @Test fun reenablingMotionDoesNotHideAnInitiallyStaticSurface() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var actual: Modifier? = null
        rule.setContent { actual = Modifier.openingModifier(); Box(checkNotNull(actual)) }
        rule.runOnIdle { assertSame(Modifier, actual) }
        changeMotionScale(1f)
        rule.runOnIdle { assertSame(Modifier, actual) }
    }

    @Test fun completingOpeningDoesNotRecreateOrReleaseTheNativeChild() = assertNativeChildRetained(false)

    @Test fun interruptingOpeningDoesNotRecreateOrReleaseTheNativeChild() = assertNativeChildRetained(true)

    private fun assertNativeChildRetained(disableMotion: Boolean) {
        var created = 0
        var released = 0
        rule.setContent {
            AndroidView(
                factory = { View(it).also { created++ } },
                modifier = Modifier.openingModifier(),
                onReset = null,
                onRelease = { released++ },
            )
        }
        if (disableMotion) changeMotionScale(0f) else rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("Removing the layer must not replace the native surface", 1, created)
            assertEquals("The still-mounted surface must not be released", 0, released)
        }
    }

    @Composable private fun Modifier.openingModifier(key: Any? = "player"): Modifier {
        activeResolver = LocalContext.current.contentResolver
        return openTransform(key)
    }

    private fun changeMotionScale(scale: Float) {
        rule.runOnIdle {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
            // Robolectric scopes registrations to a resolver; notify the one used by the Activity.
            checkNotNull(activeResolver).notifyChange(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null,
            )
            shadowOf(Looper.getMainLooper()).idle()
        }
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
    }
}
