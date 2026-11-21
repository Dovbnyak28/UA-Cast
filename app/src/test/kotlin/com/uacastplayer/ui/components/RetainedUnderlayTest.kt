package com.uacastplayer.ui.components

import android.widget.TextView
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.theme.DUR_ENTER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class RetainedUnderlayTest {
    @get:Rule val rule = createComposeRule()
    private var composeView: View? = null
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private var previousScale = 1f

    @Before fun enableMotion() {
        previousScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @After fun restoreMotion() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
    }

    @Test fun coveredContentDoesNotDrawButResumesDrawingWhenRevealed() {
        val covered = mutableStateOf(false)
        val color = mutableStateOf(Color.Red)
        var draws = 0
        rule.setContent {
            composeView = LocalView.current
            Box(Modifier.size(64.dp).background(Color.Black)) {
                RetainedUnderlay(covered = { covered.value }) {
                    Box(Modifier.size(64.dp).drawBehind { draws++; drawRect(color.value) })
                }
            }
        }
        drawFrame()
        var before = 0
        rule.runOnIdle { assertTrue(draws > 0); before = draws; covered.value = true }
        rule.waitForIdle()
        rule.runOnIdle { color.value = Color.Blue }
        drawFrame()
        rule.runOnIdle { assertEquals("Opaque player must suppress hidden paint", before, draws) }
        rule.runOnIdle { covered.value = false }
        drawFrame()
        rule.runOnIdle { assertTrue("Returning to navigation must draw its current content", draws > before) }
    }

    @Test fun openingTransitionKeepsItsUnderlayUntilTheOpaquePlayerIsReady() {
        val opaque = mutableStateOf(false)
        val color = mutableStateOf(Color.Red)
        var draws = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            composeView = LocalView.current
            Box(Modifier.size(64.dp).background(Color.Black)) {
                RetainedUnderlay(covered = { opaque.value }) {
                    Box(Modifier.size(64.dp).drawBehind { draws++; drawRect(color.value) })
                }
                Box(Modifier.size(1.dp).openTransform("opening", onOpacityChanged = { opaque.value = it }))
            }
        }
        drawFrame()
        var before = 0
        rule.runOnIdle { assertFalse(opaque.value); before = draws; color.value = Color.Blue }
        drawFrame()
        rule.runOnIdle { assertTrue("Opening must preserve the screen behind the fading player", draws > before) }
        rule.mainClock.advanceTimeBy(DUR_ENTER.toLong() * 3)
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(opaque.value); before = draws; color.value = Color.Red }
        drawFrame()
        rule.runOnIdle { assertEquals("Completed opening must stop underlay painting", before, draws) }
    }

    @Test fun hidingAndRevealingDoesNotRecreateOrReleaseEmbeddedViews() {
        val covered = mutableStateOf(false)
        val text = mutableStateOf("before")
        var view: TextView? = null
        var created = 0
        var released = 0
        rule.setContent {
            RetainedUnderlay(covered = { covered.value }) {
                AndroidView(
                    factory = { context -> TextView(context).also { view = it; created++ } },
                    update = { it.text = text.value },
                    onRelease = { released++ },
                )
            }
        }
        rule.waitForIdle()
        val original = checkNotNull(view)
        rule.runOnIdle { covered.value = true; text.value = "updated while covered" }
        rule.waitForIdle()
        rule.runOnIdle { covered.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertSame(original, view)
            assertEquals(1, created)
            assertEquals(0, released)
            assertEquals("updated while covered", original.text.toString())
        }
    }

    @Test fun removedAnimationsDoNotPaintTheUnderlayOnTheFirstOpaqueFrame() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val opaque = mutableStateOf(false)
        var draws = 0
        rule.setContent {
            composeView = LocalView.current
            Box(Modifier.size(64.dp).background(Color.Black)) {
                RetainedUnderlay(covered = { opaque.value }) {
                    Box(Modifier.size(64.dp).drawBehind { draws++; drawRect(Color.Red) })
                }
                Box(Modifier.size(1.dp).openTransform("instant", onOpacityChanged = { opaque.value = it }))
            }
        }
        drawFrame()
        rule.runOnIdle { assertTrue(opaque.value); assertEquals(0, draws) }
    }

    @Test fun coveredNavigationIsNotMeasuredOrExposedAsVisibleAccessibilityContent() {
        val covered = mutableStateOf(false)
        val width = mutableStateOf(64.dp)
        var measures = 0
        rule.setContent {
            Box(Modifier.size(width.value, 64.dp)) {
                RetainedUnderlay(covered = { covered.value }, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().testTag("underlay").layout { measurable, constraints ->
                        measures++
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                    }) { BasicText("Background destination") }
                }
            }
        }
        rule.onNodeWithText("Background destination").assertIsDisplayed()
        var before = 0
        rule.runOnIdle { before = measures; covered.value = true }
        rule.onNodeWithText("Background destination").assertIsNotDisplayed()
        rule.runOnIdle { width.value = 96.dp }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("Hidden navigation must not remeasure on rotation", before, measures) }
        rule.runOnIdle { covered.value = false }
        rule.onNodeWithText("Background destination").assertIsDisplayed()
        rule.onNodeWithTag("underlay").assertWidthIsEqualTo(96.dp)
        rule.runOnIdle { assertTrue(measures > before) }
    }

    private fun drawFrame() {
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        // Draw the owning Android view, not a capture modifier inserted below the deliberately
        // suppressed subtree. No screenshot callback is expected from hidden content.
        rule.runOnIdle {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            try { checkNotNull(composeView).draw(Canvas(bitmap)) } finally { bitmap.recycle() }
        }
    }
}
