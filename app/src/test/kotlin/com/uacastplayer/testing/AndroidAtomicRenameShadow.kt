package com.uacastplayer.testing

import android.util.Log
import androidx.core.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Windows File.renameTo cannot replace an existing file, unlike Android's rename syscall.
 * Adapt only that private syscall seam; real AtomicFile start/sync/close/rollback still execute.
 * Real Android replacement and failed-rename behavior are also covered by device regressions. */
@Implements(value = AtomicFile::class, isInAndroidSdk = false)
// Robolectric's ShadowWrangler requires a public no-arg constructor, even for static seams.
@Suppress("UtilityClassWithPublicConstructor")
class AndroidAtomicRenameShadow {
    companion object {
        @JvmStatic
        @Implementation
        fun rename(source: File, target: File) {
            if (target.isDirectory) target.delete()
            try {
                Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (failure: IOException) {
                // Match AndroidX's log-only failure; writeSafely must detect the failed commit.
                Log.e("AtomicFile", "Test rename failed", failure)
            }
        }
    }
}
