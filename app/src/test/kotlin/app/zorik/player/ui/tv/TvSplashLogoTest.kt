package app.zorik.player.ui.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import app.zorik.player.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvSplashLogoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test @Config(qualifiers = "television-mdpi")
    fun mdpiTvUsesScalableMaskSafeArtwork() { assertScalableArtwork() }
    @Test @Config(qualifiers = "television-tvdpi")
    fun tvdpiTvUsesScalableMaskSafeArtwork() { assertScalableArtwork() }
    @Test @Config(qualifiers = "television-xhdpi")
    fun xhdpiTvUsesScalableMaskSafeArtwork() { assertScalableArtwork() }
    @Test @Config(qualifiers = "mdpi")
    fun phoneUsesTheSameScalableArtwork() { assertScalableArtwork() }

    @Test @Config(qualifiers = "television-tvdpi")
    fun tvSplashAtTvdpiSize() {
        renderLogo(384).captureRoboImage("src/test/screenshots/splash_tv_tvdpi.png")
    }
    @Test @Config(qualifiers = "television-xhdpi")
    fun tvSplashAtXhdpiSize() {
        renderLogo(576).captureRoboImage("src/test/screenshots/splash_tv_xhdpi.png")
    }

    private fun themedLogo(): VectorDrawable {
        val attributes = context.obtainStyledAttributes(R.style.Theme_ZorikPlayer_Splash,
            intArrayOf(androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon))
        return try {
            val logo = requireNotNull(attributes.getDrawable(0))
            assertTrue("Phone and TV must scale vector geometry, not a decoded raster", logo is VectorDrawable)
            logo as VectorDrawable
        } finally { attributes.recycle() }
    }

    private fun assertScalableArtwork() {
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
        try {
            themedLogo().apply { setBounds(0, 0, 720, 720); draw(Canvas(bitmap)) }
            var visible = 0
            var antialiased = 0
            for (y in 0 until 720) for (x in 0 until 720) {
                val alpha = Color.alpha(bitmap.getPixel(x, y))
                if (alpha > 0) {
                    visible++
                    val dx = x + 0.5f - 360f
                    val dy = y + 0.5f - 360f
                    assertTrue("Keep visible pixels inside the splash safe circle", dx * dx + dy * dy < 240f * 240f)
                }
                if (alpha in 1..254) antialiased++
            }
            assertTrue("The rendered mark must remain legible", visible > 720 * 720 / 20)
            assertTrue("Large TV edges need antialiased coverage", antialiased > 720)
            assertTrue(bitmap.allocationByteCount <= 2_097_152)
        } finally { bitmap.recycle() }
    }

    private fun renderLogo(size: Int): Bitmap {
        val logo = themedLogo()
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(context.getColor(R.color.zorik_background))
        logo.setBounds(0, 0, size, size)
        logo.draw(canvas)
        return output
    }
}
