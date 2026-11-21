package com.uacastplayer.diagnostics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiagnosticsArchiveTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val directory: File get() = File(application.cacheDir, "diagnostics")

    @Before
    @After
    fun clearDiagnosticsDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun `rapid reports get distinct files and the first remains readable`() {
        val first = DiagnosticsArchive.writeReportFile(application, "first")
        val second = DiagnosticsArchive.writeReportFile(application, "second")

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
        assertTrue(first!!.isFile)
        assertTrue(second!!.isFile)
    }
}
