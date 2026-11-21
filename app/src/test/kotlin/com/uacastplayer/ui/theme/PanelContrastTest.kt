package com.uacastplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelContrastTest {
    @Test fun textAndSelectedNavigationStayReadableOnEveryNewSurface() {
        listOf(AzureUaPalette, CinemaUaPalette, MidnightUaPalette, DaylightUaPalette).forEach { palette ->
            val surfaces = listOf(
                panelTopColor(palette, false), panelTopColor(palette, true), panelBottomColor(palette),
            )
            // Include the largest decorative ring alpha, not only the clean fill behind text.
            (surfaces + surfaces.map { palette.azure.copy(alpha = 0.12f).compositeOver(it) }).forEach { fill ->
                listOf(palette.labelPrimary, palette.labelSecondary).forEach { foreground ->
                    assertTrue("$foreground on $fill", contrast(foreground, fill) >= 4.5f)
                }
            }
            val selected = navigationSelectionColor(palette)
            assertTrue(contrast(palette.labelPrimary, selected) >= 4.5f)
            assertTrue(contrast(palette.azure, selected) >= 3f)
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance(); val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
