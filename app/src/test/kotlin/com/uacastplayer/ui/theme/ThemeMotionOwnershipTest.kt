package com.uacastplayer.ui.theme

import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.PrimaryButton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class ThemeMotionOwnershipTest {
    @get:Rule val rule = createComposeRule()
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
    private var previousScale = 1f
    @Before fun scale() {
        previousScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    @After fun restore() { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale) }

    @Test fun oneThemeObserverServesAllControlsAndIsRemovedOnDisposal() {
        val mounted = mutableStateOf(true)
        rule.setContent { if (mounted.value) UaCastTheme(AppTheme.CINEMA) {
            Column {
                Text("motion=${motionAllowed()}")
                repeat(12) { PrimaryButton("Action $it", {}) }
            }
        } }
        rule.runOnIdle { assertEquals(1, shadowOf(resolver).getContentObservers(uri).size) }
        rule.runOnIdle { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) }
        shadowOf(Looper.getMainLooper()).idle()
        rule.onNodeWithText("motion=false").assertExists()
        rule.runOnIdle { mounted.value = false }
        rule.runOnIdle { assertEquals(0, shadowOf(resolver).getContentObservers(uri).size) }
    }

    @Test fun themeAndReducedMotionChangesRetainTheSameNativeChild() {
        val theme = mutableStateOf(AppTheme.CINEMA)
        val reduced = mutableStateOf(false)
        var created = 0
        var released = 0
        rule.setContent {
            CompositionLocalProvider(LocalReducedEffects provides reduced.value) {
                UaCastTheme(theme.value) { UaPlayerTheme {
                    AndroidView(factory = { View(it).also { created++ } }, onRelease = { released++ })
                } }
            }
        }
        repeat(2) {
            AppTheme.entries.forEach { choice ->
                rule.runOnIdle { theme.value = choice; reduced.value = !reduced.value }
                rule.runOnIdle { assertEquals(1, created); assertEquals(0, released) }
            }
        }
    }
}
