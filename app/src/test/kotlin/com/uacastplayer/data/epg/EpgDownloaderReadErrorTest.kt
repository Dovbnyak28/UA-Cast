package com.uacastplayer.data.epg

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What a failed download is allowed to remember about itself.
 *
 * [EpgFailureReason] puts this value into the diagnostics report a user emails, so the contract is
 * enforced here at the producer: the *class* of the exception, never its message. An OkHttp
 * IOException names the URL it failed on, and an Xtream feed's URL carries the account's username
 * and password in its query string.
 */
class EpgDownloaderReadErrorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Port 1 with nothing behind it: refused immediately, offline, on every machine. */
    private val deadUrl = "http://127.0.0.1:1/xmltv.xml?username=bob&password=hunter2"

    private fun download(url: String): EpgDownloadResult =
        runBlocking { EpgDownloader(OkHttpClient(), tempFolder.newFolder()).download(url) }

    @Test
    fun `a refused connection is reported as the exception's class name`() {
        val result = download(deadUrl)
        assertTrue(result.toString(), result is EpgDownloadResult.ReadError)
        assertEquals("ConnectException", (result as EpgDownloadResult.ReadError).cause)
    }

    /**
     * The reason this is a class name and not `e.message`. Nothing downstream sanitizes it - the
     * report reads the value as given - so the credentials must never be in it to begin with.
     */
    @Test
    fun `the reported cause never carries the url it failed on`() {
        val result = download(deadUrl) as EpgDownloadResult.ReadError
        val cause = result.cause.orEmpty()
        assertTrue("credentials leaked into a shared report: $cause", "password" !in cause)
        assertTrue("the url leaked into a shared report: $cause", "127.0.0.1" !in cause)
    }

    @Test
    fun `a malformed imported url is a read error rather than an uncaught exception`() {
        val result = download("not a valid XMLTV URL")

        assertEquals(EpgDownloadResult.ReadError("IllegalArgumentException"), result)
    }
}
