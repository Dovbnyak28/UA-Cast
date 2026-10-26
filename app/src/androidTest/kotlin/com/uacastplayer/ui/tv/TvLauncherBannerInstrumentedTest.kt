package com.uacastplayer.ui.tv

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uacastplayer.MainActivity
import com.uacastplayer.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reads the actual platform/launcher drawable; never changes playlists, settings or Activity state. */
@RunWith(AndroidJUnit4::class)
class TvLauncherBannerInstrumentedTest {
    @Test fun launcherLoadsFullResolutionFilteredBitmap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        assertTrue("Only test the isolated debug package", context.packageName.endsWith(".debug"))
        // Some system images choose the manifest's roundIcon as ApplicationInfo.icon.
        // Both are phone launcher assets; neither may be replaced by the TV banner.
        assertTrue("Phone icon must remain one of the two manifest-declared launcher resources",
            context.applicationInfo.icon in setOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round))
        val activity = manager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val drawable = requireNotNull(activity.loadBanner(manager))
        assertTrue("Legacy launchers should not have to rasterize the vector", drawable is BitmapDrawable)
        val banner = drawable as BitmapDrawable
        assertEquals(640, banner.bitmap.width)
        assertEquals(360, banner.bitmap.height)
        assertTrue(banner.isFilterBitmap)
        assertTrue(banner.bitmap.allocationByteCount <= 1_048_576)
        val small = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        try {
            banner.setBounds(0, 0, small.width, small.height)
            banner.draw(Canvas(small))
            for (y in 0 until small.height) for (x in 0 until small.width) {
                assertEquals("The launcher tile must be opaque", 255, Color.alpha(small.getPixel(x, y)))
            }
            val evidence = File(context.cacheDir, "tv-banner-native-filtered.png")
            evidence.outputStream().use { assertTrue(small.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { small.recycle() }
    }
}
