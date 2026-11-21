package com.uacastplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.uacastplayer.testing.RequiresComposeTestManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Category(RequiresComposeTestManifest::class)
class AppearanceBehaviorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lightChromeKeepsDarkVideoControlsWithoutRecreatingTheirComposition() {
        val theme = mutableStateOf(AppTheme.DAYLIGHT)
        var rootDark = true
        var playerDark = false
        var owner: Any? = null
        var starts = 0
        rule.setContent { UaCastTheme(theme.value) {
            rootDark = UaTheme.palette.isDark
            assertEquals(UaTheme.palette.void, MaterialTheme.colorScheme.background)
            UaPlayerTheme {
                playerDark = UaTheme.palette.isDark
                owner = remember { Any() }
                DisposableEffect(Unit) { starts++; onDispose { } }
                Text("Video controls")
            }
        } }
        rule.runOnIdle { assertFalse(rootDark); assertTrue(playerDark) }
        val first = owner
        rule.runOnIdle { theme.value = AppTheme.CINEMA }
        rule.runOnIdle { assertSame(first, owner); assertEquals(1, starts); assertTrue(playerDark) }
    }

    @Test fun reducedModeOverridesThePlatformAnimationPreference() {
        var allowed = true
        rule.setContent {
            CompositionLocalProvider(LocalReducedEffects provides true) {
                allowed = com.uacastplayer.ui.components.animationsAllowed()
                Text("Reduced effects")
            }
        }
        rule.runOnIdle { assertFalse(allowed) }
    }

    @Test fun systemNightChangeKeepsThePlayerOwnerAndChangesOnlyTheChromePalette() {
        val night = mutableStateOf(false)
        var dark = true
        var owner: Any? = null
        var mounts = 0
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                UaCastTheme(AppTheme.SYSTEM) {
                    dark = UaTheme.palette.isDark
                    UaPlayerTheme {
                        owner = remember { Any() }
                        DisposableEffect(Unit) { mounts++; onDispose { } }
                        Text("Video")
                    }
                }
            }
        }
        rule.runOnIdle { assertFalse(dark) }
        val first = owner
        rule.runOnIdle { night.value = true }
        rule.runOnIdle { assertTrue(dark); assertSame(first, owner); assertEquals(1, mounts) }
    }
}
