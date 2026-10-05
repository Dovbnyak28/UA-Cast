package com.uacastplayer.core.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The hash a downloaded update is checked against.
 *
 * Pinned to published vectors rather than to itself: a digest that only agrees with this app is
 * worth nothing, because the value it has to match is the one GitHub computed. The empty-input
 * vector is the standard SHA-256 of zero bytes.
 */
class FileDigestTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun fileOf(bytes: ByteArray): File = folder.newFile().apply { writeBytes(bytes) }

    @Test
    fun `an empty file hashes to the published empty sha256`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            FileDigest.sha256(fileOf(ByteArray(0))),
        )
    }

    @Test
    fun `abc hashes to the published vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FileDigest.sha256(fileOf("abc".toByteArray())),
        )
    }

    /**
     * Larger than the 8KB chunk this reads in, so the multi-chunk path is what runs - an update APK
     * is tens of megabytes and never fits one chunk. A digest that were only ever exercised on
     * short input could be wrong on every real file and still pass the vectors above.
     */
    @Test
    fun `a file larger than one chunk hashes the whole of it`() {
        val bytes = ByteArray(70_000) { (it % 251).toByte() }
        val hash = FileDigest.sha256(fileOf(bytes))

        assertEquals(64, hash?.length)
        // One byte changed at the far end: a reader that stopped at the first chunk would agree.
        val tampered = bytes.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
        assertNotEquals(hash, FileDigest.sha256(fileOf(tampered)))
    }

    @Test
    fun `the hex is lowercase, which is the form the published digest is compared in`() {
        val hash = FileDigest.sha256(fileOf("anything".toByteArray()))!!

        assertEquals(hash.lowercase(), hash)
    }

    /** A file that cannot be read is "not verified", which is the same answer as a mismatch - never
     * an exception escaping into a download coroutine. */
    @Test
    fun `a missing file is null rather than a throw`() {
        assertNull(FileDigest.sha256(File(folder.root, "was-never-written.apk")))
    }
}
