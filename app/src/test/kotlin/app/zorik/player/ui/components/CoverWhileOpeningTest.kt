package app.zorik.player.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.view.drawToBitmap
import app.zorik.player.testing.RequiresComposeTestManifest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The cover is what keeps the player's SurfaceView out of sight while the opening layer runs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Category(RequiresComposeTestManifest::class)
class CoverWhileOpeningTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var root: View

    @Test fun coversTheContentOnlyWhileTheOpeningRuns() {
        val opening = mutableStateOf(true)
        rule.setContent {
            root = LocalView.current
            CompositionLocalProvider(LocalOpeningInProgress provides { opening.value }) {
                Box(Modifier.fillMaxSize().background(Color.White).coverWhileOpening())
            }
        }
        assertEquals(Color.Black.toArgb(), centrePixel())

        rule.runOnIdle { opening.value = false }
        assertEquals("Ending the opening must drop the cover", Color.White.toArgb(), centrePixel())
    }

    @Test fun drawsNothingWithoutAnEnclosingOpening() {
        rule.setContent {
            root = LocalView.current
            Box(Modifier.fillMaxSize().background(Color.White).coverWhileOpening())
        }
        assertEquals(Color.White.toArgb(), centrePixel())
    }

    private fun centrePixel(): Int = rule.runOnIdle {
        val bitmap = root.drawToBitmap()
        try {
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally {
            bitmap.recycle()
        }
    }
}
