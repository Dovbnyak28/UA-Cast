package com.uacastplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppCacheFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** An APK the removed self-updater left behind is deleted; other cache contents stay. */
    @Test
    fun retiredUpdateDownloadsAreDeletedAndNothingElse() {
        val cacheDir = temp.newFolder("cache")
        val updates = File(cacheDir, "updates").apply { mkdirs() }
        File(updates, "abc.apk").writeBytes(ByteArray(16))
        File(updates, "def.apk.part").writeBytes(ByteArray(8))
        val diagnostics = File(cacheDir, "diagnostics/report.zip").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(4))
        }

        AppCacheFiles.deleteRetiredUpdateDownloads(cacheDir)

        assertFalse(updates.exists())
        assertTrue(diagnostics.exists())
    }

    @Test
    fun aMissingUpdatesDirectoryIsNotAnError() {
        val cacheDir = temp.newFolder("cache")
        AppCacheFiles.deleteRetiredUpdateDownloads(cacheDir)
        assertFalse(File(cacheDir, "updates").exists())
    }
}
