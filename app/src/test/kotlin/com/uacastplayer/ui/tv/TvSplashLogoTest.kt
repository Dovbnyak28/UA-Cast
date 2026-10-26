package com.uacastplayer.ui.tv

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.R
import org.junit.Assert.assertEquals
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
    fun mdpiTvKeepsFullResolutionBeforeSplashScaling() { assertTvMaster() }

    @Test @Config(qualifiers = "television-tvdpi")
    fun tvdpiTvKeepsFullResolutionBeforeSplashScaling() { assertTvMaster() }

    @Test @Config(qualifiers = "television-xhdpi")
    fun xhdpiTvKeepsFullResolutionBeforeSplashScaling() { assertTvMaster() }

    @Test @Config(qualifiers = "mdpi")
    fun phoneKeepsItsExistingDensityScaledAsset() {
        assertTrue(context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK !=
            Configuration.UI_MODE_TYPE_TELEVISION)
        assertEquals(180, themedLogo().bitmap.width)
        assertEquals(180, themedLogo().bitmap.height)
    }

    @Test @Config(qualifiers = "television-tvdpi")
    fun tvSplashAtTvdpiSize() {
        renderLogo(384).captureRoboImage("src/test/screenshots/splash_tv_tvdpi.png")
    }

    @Test @Config(qualifiers = "television-xhdpi")
    fun tvSplashAtXhdpiSize() {
        renderLogo(576).captureRoboImage("src/test/screenshots/splash_tv_xhdpi.png")
    }

    private fun themedLogo(): BitmapDrawable {
        val attributes = context.obtainStyledAttributes(R.style.Theme_UaCastPlayer_Splash,
            intArrayOf(androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon))
        return try {
            requireNotNull(attributes.getDrawable(0)) as BitmapDrawable
        } finally {
            attributes.recycle()
        }
    }

    private fun assertTvMaster(): BitmapDrawable = themedLogo().also { logo ->
        assertEquals(Configuration.UI_MODE_TYPE_TELEVISION,
            context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK)
        assertEquals("Do not decode at TV density and then enlarge those reduced pixels", 720, logo.bitmap.width)
        assertEquals(720, logo.bitmap.height)
        assertTrue("The final splash scale must filter pixels", logo.isFilterBitmap)
        assertTrue("Keep the one splash bitmap within two MiB", logo.bitmap.allocationByteCount <= 2_097_152)
        // Keep the existing transparent safe zone for Android's splash icon mask.
        for (point in listOf(0 to 0, 119 to 360, 600 to 360, 360 to 119, 360 to 600)) {
            assertEquals(0, Color.alpha(logo.bitmap.getPixel(point.first, point.second)))
        }
    }

    private fun renderLogo(size: Int): Bitmap {
        val logo = assertTvMaster()
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(context.getColor(R.color.uacast_background))
        logo.setBounds(0, 0, size, size)
        logo.draw(canvas)
        return output
    }
}
