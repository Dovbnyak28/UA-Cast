package com.uacastplayer.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApkStagingTest {
    @Test fun `complete staging preserves all bytes including final short chunk`() {
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE * 2 + 31) { it.toByte() }
        val output = ByteArrayOutputStream()
        copyStagedApk(bytes.inputStream(), output) {}
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test fun `cancellation stops staging between bounded writes`() {
        val output = ByteArrayOutputStream()
        assertThrows(CancellationException::class.java) {
            copyStagedApk(ByteArray(DEFAULT_BUFFER_SIZE * 3).inputStream(), output) {
                if (output.size() > 0) throw CancellationException("owner gone")
            }
        }
        assertEquals(DEFAULT_BUFFER_SIZE, output.size())
    }

    @Test fun `storage failure propagates instead of publishing a partial APK`() {
        val fullDisk = object : OutputStream() {
            override fun write(value: Int) { throw IOException("disk full") }
        }
        assertThrows(IOException::class.java) {
            copyStagedApk(ByteArray(32).inputStream(), fullDisk) {}
        }
    }

    @Test fun `failed staging abandons the system session`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val before = context.packageManager.packageInstaller.mySessions.map { it.sessionId }
        val absent = File(context.cacheDir, "absent-update.apk")
        assertEquals(InstallLaunch.Failed, ApkInstaller.commitSession(context, absent))
        assertEquals(before, context.packageManager.packageInstaller.mySessions.map { it.sessionId })
    }
}
