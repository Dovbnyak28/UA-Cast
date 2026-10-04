package com.uacastplayer.ui.tv

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "television-xhdpi")
class TvLauncherBannerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun launcherUsesTvBannerWithoutReplacingPhoneIcon() {
        val launch = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
        assertEquals(ComponentName(context, MainActivity::class.java), launch?.component)
        assertEquals(R.drawable.tv_banner, context.applicationInfo.banner)
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        val activity = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertNotNull(activity.loadBanner(context.packageManager))
        assertHdBitmap()
    }

    @Test @Config(qualifiers = "television-tvdpi")
    fun oldTvDensityMustNotDownsampleThePackagedMaster() { assertHdBitmap() }

    @Test fun tvBannerAtStandardLauncherSize() {
        renderBanner(320, 180).captureRoboImage("src/test/screenshots/tv_launcher_banner.png")
    }

    @Test fun tvBannerAtSmallLauncherSize() {
        renderBanner(160, 90).captureRoboImage("src/test/screenshots/tv_launcher_banner_small.png")
    }

    @Test fun hdBannerMasterIsGeneratedFromVectorSource() {
        // Render contours at 4x the shipped size, then filter down once. Do not enlarge a small PNG.
        val artwork = requireNotNull(context.getDrawable(R.drawable.tv_banner_artwork))
        val oversized = Bitmap.createBitmap(2560, 1440, Bitmap.Config.ARGB_8888)
        artwork.setBounds(0, 0, oversized.width, oversized.height)
        artwork.draw(Canvas(oversized))
        val master = Bitmap.createScaledBitmap(oversized, 640, 360, true)
        master.captureRoboImage("src/test/screenshots/tv_banner_hd_master.png")
        val packaged = assertHdBitmap().bitmap
        val generatedPixels = IntArray(master.width * master.height)
        val packagedPixels = IntArray(packaged.width * packaged.height)
        master.getPixels(generatedPixels, 0, master.width, 0, 0, master.width, master.height)
        packaged.getPixels(packagedPixels, 0, packaged.width, 0, 0, packaged.width, packaged.height)
        // Compare every visible ARGB pixel independently of native bitmap allocation/config metadata.
        assertArrayEquals("Generated and packaged pixels must match; configs=${master.config}/${packaged.config}",
            generatedPixels, packagedPixels)
        master.recycle()
        oversized.recycle()
    }

    @Test fun wordmarkAttributionIsPackaged() {
        val license = context.assets.open("licenses/tv-wordmark-roboto.txt").bufferedReader().use { it.readText() }
        assertTrue(license.contains("Copyright 2011 Google Inc."))
        assertTrue(license.contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
    }

    private fun renderBanner(width: Int, height: Int): Bitmap {
        // Load through PackageManager, as the launcher does, not a separate design mock-up.
        val banner = requireNotNull(context.applicationInfo.loadBanner(context.packageManager))
        assertEquals(banner.intrinsicWidth * 9, banner.intrinsicHeight * 16)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        banner.setBounds(0, 0, width, height)
        banner.draw(Canvas(bitmap))
        var foregroundPixels = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                assertEquals("The tile must have an opaque background", 255, Color.alpha(pixel))
                if (Color.red(pixel) > 220 || Color.green(pixel) > 170) {
                    foregroundPixels++
                    assertTrue("Keep the mark and name inside the safe margin", x >= width / 20 &&
                        x < width - width / 20 && y >= height / 10 && y < height - height / 10)
                }
            }
        }
        assertTrue("The name and mark must survive downscaling", foregroundPixels > width * height / 30)
        return bitmap
    }

    private fun assertHdBitmap(): BitmapDrawable {
        val banner = requireNotNull(context.applicationInfo.loadBanner(context.packageManager))
        assertTrue("Launchers must receive a filtered bitmap, not rasterize a vector", banner is BitmapDrawable)
        return (banner as BitmapDrawable).also {
            assertEquals(640, it.bitmap.width)
            assertEquals(360, it.bitmap.height)
            assertTrue("Focus zoom/downscaling must use bitmap filtering", it.isFilterBitmap)
            assertTrue("Keep decoded launcher memory below one MiB", it.bitmap.allocationByteCount <= 1_048_576)
        }
    }
}
