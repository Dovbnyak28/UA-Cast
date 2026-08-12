package com.uacastplayer.data

import androidx.core.util.AtomicFile
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [writeSafely] exists for one reason: every store built on it writes from a fire-and-forget
 * `scope.launch`, where an uncaught throw reaches the thread's default handler and kills the app.
 * So the contract under test is not "the write succeeded" - it is "a write that cannot happen
 * returns false and nothing escapes".
 *
 * Robolectric because `AtomicFile` logs through `android.util.Log`, and so does `AppLog` on the
 * failure path; on a bare JVM both throw "not mocked" and the test would pass or fail for the
 * wrong reason.
 */
@RunWith(RobolectricTestRunner::class)
class AtomicFileIoTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a write that works reports success and leaves the bytes behind`() {
        val target = File(folder.newFolder(), "favorites.bin")

        val wrote = AtomicFile(target).writeSafely("Test", "Favorites") { it.write(byteArrayOf(1, 2, 3)) }

        assertTrue(wrote)
        assertTrue(target.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    /**
     * Failing part-way through the write was always handled. This is the other half.
     */
    @Test
    fun `a failure inside the write is swallowed and reported`() {
        val target = File(folder.newFolder(), "favorites.bin")

        val wrote = AtomicFile(target).writeSafely("Test", "Favorites") { error("codec blew up") }

        assertFalse(wrote)
    }

    /**
     * The file cannot be opened at all - which is what a full disk, a removed volume or a vanished
     * directory actually looks like, and is the failure this whole function was written to survive.
     *
     * `AtomicFile.startWrite` throws `IOException` on its own when it can neither open the file nor
     * create the directory holding it, and it used to be called on the line *above* the `try`. The
     * throw went straight out of here into a `scope.launch` with no catch anywhere on the way, so
     * starring a channel on a device that could not write took the app down - the exact crash the
     * broad catch below it was added to prevent.
     *
     * Reproduced by putting a regular file where the parent directory should be: opening fails,
     * `mkdirs()` on a path already occupied by a file fails too, and `startWrite` gives up.
     */
    @Test
    fun `a file that cannot even be opened returns false instead of throwing`() {
        val blocked = folder.newFile("not-a-directory")
        val target = File(blocked, "favorites.bin")

        val wrote = AtomicFile(target).writeSafely("Test", "Favorites") { it.write(byteArrayOf(1)) }

        assertFalse(wrote)
    }
}
