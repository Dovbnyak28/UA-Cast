package app.zorik.player.ui.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherBrandingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun foregroundKeepsItsGeometryInsideEveryAdaptiveMask() {
        val bitmap = render(R.drawable.ic_launcher_foreground, 720)
        try {
            var visible = 0
            for (y in 0 until 720) for (x in 0 until 720) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                visible++
                val dx = x + 0.5f - 360f
                val dy = y + 0.5f - 360f
                assertTrue("Keep the coloured mark inside the 66/108 adaptive safe circle",
                    dx * dx + dy * dy < 220f * 220f)
            }
            assertTrue("Safe padding must not hide the mark", visible > 720 * 720 / 20)
        } finally { bitmap.recycle() }
    }

    @Test fun themedIconPreservesTheFullSilhouette() {
        val foreground = render(R.drawable.ic_launcher_foreground, 216)
        val monochrome = render(R.drawable.ic_launcher_monochrome, 216)
        try {
            var colouredPixels = 0
            for (y in 0 until 216) for (x in 0 until 216) {
                val normal = foreground.getPixel(x, y)
                val themed = monochrome.getPixel(x, y)
                assertEquals("Themed icons must retain screen, play and transmission strokes",
                    Color.alpha(normal), Color.alpha(themed))
                if (Color.alpha(themed) == 0) continue
                assertEquals(Color.red(themed), Color.green(themed))
                assertEquals(Color.green(themed), Color.blue(themed))
                if (Color.red(normal) != Color.blue(normal)) colouredPixels++
            }
            assertTrue("The normal mark retains its blue/yellow accents", colouredPixels > 100)
        } finally { foreground.recycle(); monochrome.recycle() }
    }

    @Test fun launcherLoadsTheActualAdaptiveIcon() {
        val icon = requireNotNull(context.applicationInfo.loadIcon(context.packageManager))
        val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        try {
            icon.setBounds(0, 0, 216, 216)
            icon.draw(Canvas(bitmap))
            bitmap.captureRoboImage("src/test/screenshots/launcher_adaptive.png")
        } finally { bitmap.recycle() }
    }

    private fun render(id: Int, size: Int): Bitmap {
        val drawable = requireNotNull(context.getDrawable(id))
        assertTrue("No old density-qualified PNG may override the shared vector", drawable is VectorDrawable)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(it))
        }
    }
}
