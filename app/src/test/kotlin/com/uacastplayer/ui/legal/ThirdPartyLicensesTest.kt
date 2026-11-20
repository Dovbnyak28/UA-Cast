package com.uacastplayer.ui.legal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThirdPartyLicensesTest {
    private val assets get() = ApplicationProvider.getApplicationContext<Context>().assets

    @Test fun everyVisibleLicenceHasItsFullTextInTheApplication() {
        thirdPartyLicenseFiles.values.forEach { name ->
            val paragraphs = readLicenseParagraphs(assets, name)
            assertTrue(name, paragraphs.isNotEmpty())
            assertTrue(name, paragraphs.sumOf { it.length } > 500)
        }
        val lgpl = readLicenseParagraphs(assets, "ffmpeg-lgpl-2.1.txt").joinToString("\n")
            .replace(Regex("\\s+"), " ")
        assertTrue(lgpl.contains("GNU LESSER GENERAL PUBLIC LICENSE"))
        assertTrue(lgpl.contains("reverse engineering"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun externalPathsCannotBeReadByTheLicenceViewer() {
        readLicenseParagraphs(assets, "../private-backup.json")
    }
}
