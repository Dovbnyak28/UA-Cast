package app.zorik.player.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusFillContrastTest {
    @Test fun filledErrorSurfacesUseReadableTextInEveryPalette() {
        listOf(AzureUaPalette, CinemaUaPalette, MidnightUaPalette, DaylightUaPalette).forEach { palette ->
            val scheme = colorSchemeFor(palette)
            val a = scheme.error.luminance()
            val b = scheme.onError.luminance()
            val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
            assertTrue("Error text contrast is $ratio for ${palette.routeRed}", ratio >= 4.5f)
        }
    }
}
