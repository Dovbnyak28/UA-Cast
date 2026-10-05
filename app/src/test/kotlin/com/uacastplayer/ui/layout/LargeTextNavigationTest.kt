package com.uacastplayer.ui.layout

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.uacastplayer.core.nav.BottomDestination
import com.uacastplayer.testing.RequiresComposeTestManifest
import com.uacastplayer.ui.components.GlassTabBar
import com.uacastplayer.ui.components.TabBarItem
import com.uacastplayer.ui.nav.largeTextLabelRes
import com.uacastplayer.ui.nav.tabLabelRes
import com.uacastplayer.ui.theme.AppIcons
import com.uacastplayer.ui.theme.AppTheme
import com.uacastplayer.ui.theme.UaCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h480dp-xhdpi")
@Category(RequiresComposeTestManifest::class)
class LargeTextNavigationTest(private val locale: String) {
    @get:Rule val rule = createComposeRule()

    @Test fun labelsDoNotBreakMidWordAtTwoHundredPercent() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h480dp-xhdpi")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                UaCastTheme(AppTheme.CINEMA) {
                    GlassTabBar(BottomDestination.entries.map { destination ->
                        TabBarItem(
                            label = destination.name,
                            largeTextLabel = stringResource(destination.largeTextLabelRes()),
                            icon = AppIcons.Channels,
                            selected = destination == BottomDestination.SETTINGS,
                            onClick = {},
                        )
                    })
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        BottomDestination.entries.forEach { destination ->
            val results = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(context.getString(destination.largeTextLabelRes()))
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            val diagnostic = "$locale $destination size=${layout.size} constraints=${layout.layoutInput.constraints} " +
                "left=${layout.getLineLeft(0)} right=${layout.getLineRight(0)} bottom=${layout.getLineBottom(0)}"
            assertEquals("$diagnostic line count", 1, layout.lineCount)
            assertFalse("$diagnostic ellipsis", layout.isLineEllipsized(0))
            // Center alignment offsets line coordinates inside the paragraph. Compare the ink
            // width, not its right coordinate, with the measured text width (rounded to pixels).
            val lineWidth = layout.getLineRight(0) - layout.getLineLeft(0)
            assertTrue("$diagnostic width", lineWidth <= layout.size.width + 1f)
            assertTrue("$diagnostic height", layout.getLineBottom(0) <= layout.size.height + 1f)
        }
    }

    @Test fun actionLabelsRemainReadableOnCompactPhones() {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h480dp-xhdpi")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                UaCastTheme(AppTheme.CINEMA) {
                    GlassTabBar(BottomDestination.entries.map { destination ->
                        TabBarItem(
                            label = stringResource(destination.tabLabelRes()),
                            largeTextLabel = stringResource(destination.largeTextLabelRes()),
                            icon = AppIcons.Channels,
                            selected = destination == BottomDestination.CHANNELS,
                            onClick = {},
                        )
                    })
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        BottomDestination.entries.forEach { destination ->
            val results = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(context.getString(destination.tabLabelRes()))
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            val diagnostic = "$locale $destination size=${layout.size} " +
                "constraints=${layout.layoutInput.constraints}"
            assertTrue("$diagnostic lines", layout.lineCount in 1..2)
            assertFalse("$diagnostic ellipsis", (0 until layout.lineCount).any(layout::isLineEllipsized))
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "locale={0}")
        fun locales() = listOf(arrayOf("en"), arrayOf("uk"), arrayOf("ru"), arrayOf("es"))
    }
}
